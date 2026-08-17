package com.rodrilang.librarymanager.purchasing.requirement.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.requirement.dto.PurchaseRequirementFilter;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementReasonResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementSummaryResponse;
import com.rodrilang.librarymanager.purchasing.requirement.mapper.PurchaseRequirementMapper;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSource;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementRepository;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementSourceRepository;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementSpecifications;
import com.rodrilang.librarymanager.purchasing.requirement.repository.projection.PurchaseRequirementReasonProjection;
import com.rodrilang.librarymanager.purchasing.requirement.service.PurchaseRequirementService;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequirementServiceImpl implements PurchaseRequirementService {

    private final PurchaseRequirementRepository requirementRepository;
    private final PurchaseRequirementSourceRepository sourceRepository;

    private final ProviderBookRepository providerBookRepository;
    private final PriceListProviderRepository providerRepository;

    private final InventoryRepository inventoryRepository;

    private final PurchaseRequirementMapper mapper;

    private final BookService bookService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    @Transactional
    @Override
    public PurchaseRequirementResponse addManualRequirement(AddPurchaseRequirementCommand command) {

        validateAdd(command);
        validateManualSource(command.source());

        return createOrAccumulateRequirement(command);
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse addRequirement(AddPurchaseRequirementCommand command) {

        validateAdd(command);

        return createOrAccumulateRequirement(command);
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse adjust(Long requirementId, Integer quantity) {

        if (quantity == null || quantity < 0) {
            throw new BusinessException("La cantidad no puede ser negativa.");
        }

        PurchaseRequirement requirement = getPendingRequirementForUpdate(requirementId);

        int currentQuantity = requirement.getQuantity();

        if (quantity == currentQuantity) {
            return mapper.toResponse(requirement);
        }

        int delta = quantity - currentQuantity;

        PurchaseRequirementSource adjustment =
                PurchaseRequirementSource.builder()
                        .requirement(requirement)
                        .type(PurchaseRequirementSourceType.ADJUSTMENT)
                        .quantity(delta)
                        .build();

        sourceRepository.save(adjustment);

        if (quantity == 0) {

            requirement.setStatus(PurchaseRequirementStatus.CANCELLED);

            return mapper.toResponse(requirement);
        }

        requirement.setQuantity(quantity);

        return mapper.toResponse(requirement);
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse assignProvider(Long requirementId, Long providerId) {

        PurchaseRequirement requirement = getPendingRequirementForUpdate(requirementId);

        if (providerId == null) {

            requirement.setPreferredProvider(null);

            return mapper.toResponse(requirement);
        }

        PriceListProvider provider = resolveProvider(providerId, requirement.getBook().getId());

        requirement.setPreferredProvider(provider);

        return mapper.toResponse(requirement);
    }

    @Transactional
    @Override
    public void cancel(Long requirementId) {

        PurchaseRequirement requirement = getPendingRequirementForUpdate(requirementId);

        PurchaseRequirementSource adjustment =
                PurchaseRequirementSource.builder()
                        .requirement(requirement)
                        .type(PurchaseRequirementSourceType.ADJUSTMENT)
                        .quantity(-requirement.getQuantity())
                        .build();

        sourceRepository.save(adjustment);

        requirement.setStatus(PurchaseRequirementStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    @Override
    public PurchaseRequirementResponse findById(Long requirementId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseRequirement requirement =
                requirementRepository
                        .findByIdAndBookstoreId(
                                requirementId,
                                bookstoreId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No se encontró la necesidad de compra con ID: " + requirementId)
                        );

        return mapper.toResponse(requirement);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PurchaseRequirementSummaryResponse> findAll(
            PurchaseRequirementFilter filter,
            Pageable pageable
    ) {

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        Specification<PurchaseRequirement> specification =
                Specification.allOf(
                        PurchaseRequirementSpecifications.bookstoreId(bookstoreId),
                        PurchaseRequirementSpecifications.search(filter.query()),
                        PurchaseRequirementSpecifications.providerId(filter.providerId()),
                        PurchaseRequirementSpecifications.status(filter.status())
                );

        Page<PurchaseRequirement> page = requirementRepository.findAll(specification, pageable);

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        List<PurchaseRequirement> requirements =
                page.getContent();

        List<Long> bookIds =
                requirements.stream()
                        .map(requirement -> requirement.getBook().getId())
                        .toList();

        List<Long> requirementIds =
                requirements.stream()
                        .map(PurchaseRequirement::getId)
                        .toList();

        Map<Long, Inventory> inventoryByBookId =
                inventoryRepository
                        .findAllByBookstoreIdAndBookIdInAndConditionAndActiveTrue(
                                bookstoreId,
                                bookIds,
                                BookCondition.NEW
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        inventory ->
                                                inventory.getBook().getId(),
                                        Function.identity()
                                )
                        );

        Map<Long, List<PurchaseRequirementReasonResponse>>
                reasonsByRequirementId =
                sourceRepository
                        .findGroupedReasons(requirementIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        PurchaseRequirementReasonProjection::getRequirementId,
                                        Collectors.mapping(
                                                reason ->
                                                        new PurchaseRequirementReasonResponse(
                                                                reason.getType(),
                                                                Math.toIntExact(
                                                                        reason.getQuantity()
                                                                )
                                                        ),
                                                Collectors.toList()
                                        )
                                )
                        );

        return page.map(requirement -> {

            Inventory inventory = inventoryByBookId.get(requirement.getBook().getId());

            List<PurchaseRequirementReasonResponse> reasons =
                    reasonsByRequirementId.getOrDefault(
                            requirement.getId(),
                            List.of()
                    );

            return mapper.toSummaryResponse(requirement, inventory, reasons);
        });
    }

    private PurchaseRequirementResponse createOrAccumulateRequirement(AddPurchaseRequirementCommand command) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Book book = bookService.getEntityById(command.bookId());

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        PriceListProvider provider = resolveProvider(command.providerId(), command.bookId());

        PurchaseRequirement requirement =
                requirementRepository
                        .findByBookstoreAndBookAndStatusForUpdate(
                                bookstoreId,
                                command.bookId(),
                                PurchaseRequirementStatus.PENDING
                        )
                        .orElseGet(() ->
                                PurchaseRequirement.builder()
                                        .bookstore(bookstore)
                                        .book(book)
                                        .quantity(0)
                                        .status(PurchaseRequirementStatus.PENDING)
                                        .build()
                        );

        requirement.setQuantity(requirement.getQuantity() + command.quantity());

        if (requirement.getPreferredProvider() == null && provider != null) {
            requirement.setPreferredProvider(provider);
        }

        requirement = requirementRepository.save(requirement);

        PurchaseRequirementSource source =
                PurchaseRequirementSource.builder()
                        .requirement(requirement)
                        .type(command.source())
                        .quantity(command.quantity())
                        .referenceId(command.referenceId())
                        .provider(provider)
                        .build();

        sourceRepository.save(source);

        return mapper.toResponse(requirement);
    }

    private PurchaseRequirement getPendingRequirementForUpdate(Long requirementId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return requirementRepository
                .findByIdAndBookstoreIdAndStatusForUpdate(
                        requirementId,
                        bookstoreId,
                        PurchaseRequirementStatus.PENDING
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException("No se encontró una necesidad de compra pendiente con ID: " + requirementId)
                );
    }

    private PriceListProvider resolveProvider(
            Long providerId,
            Long bookId
    ) {

        if (providerId == null) {
            return null;
        }

        PriceListProvider provider =
                providerRepository
                        .findById(providerId)
                        .filter(PriceListProvider::isActive)
                        .orElseThrow(() ->
                                new BusinessException("El proveedor seleccionado no se encuentra activo.")
                        );

        boolean available =
                providerBookRepository
                        .existsByProviderIdAndBookIdAndActiveTrue(
                                providerId,
                                bookId
                        );

        if (!available) {
            throw new BusinessException("El proveedor seleccionado no comercializa este libro.");
        }

        return provider;
    }

    private void validateAdd(AddPurchaseRequirementCommand command) {

        if (command == null) {
            throw new BusinessException("Debe especificarse la necesidad de compra.");
        }

        if (command.bookId() == null) {
            throw new BusinessException("Debe especificarse el libro.");
        }

        if (command.quantity() == null || command.quantity() <= 0) {
            throw new BusinessException("La cantidad debe ser mayor a cero.");
        }

        if (command.source() == null) {
            throw new BusinessException("Debe especificarse el origen de la necesidad.");
        }

        validateReference(command);
    }

    private void validateReference(AddPurchaseRequirementCommand command) {

        if (command.source() == PurchaseRequirementSourceType.SALE
                && (command.referenceId() == null || command.referenceId().isBlank())
        ) {
            throw new BusinessException("Una reposición originada por una venta debe indicar la venta de referencia.");
        }
    }

    private void validateManualSource(PurchaseRequirementSourceType source) {

        if (
                source != PurchaseRequirementSourceType.INVENTORY
                        && source != PurchaseRequirementSourceType.CATALOG
                        && source != PurchaseRequirementSourceType.MANUAL
        ) {
            throw new BusinessException("El origen informado no puede utilizarse manualmente.");
        }
    }
}