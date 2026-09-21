package com.rodrilang.librarymanager.purchasing.requirement.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderItemRepository;
import com.rodrilang.librarymanager.purchasing.order.repository.projection.PurchaseRequirementOrderedQuantityProjection;
import com.rodrilang.librarymanager.purchasing.requirement.dto.PurchaseRequirementFilter;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.AddPurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.BookPurchaseRequirementStatusResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementProviderResponse;
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
import com.rodrilang.librarymanager.purchasing.requirement.repository.projection.PurchaseRequirementProviderProjection;
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
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    private final ProviderBookRepository providerBookRepository;
    private final ProviderRepository providerRepository;

    private final InventoryRepository inventoryRepository;

    private final PurchaseRequirementMapper purchaseRequirementMapper;

    private final BookService bookService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    @Transactional
    @Override
    public AddPurchaseRequirementResponse addManualRequirement(AddPurchaseRequirementCommand command) {

        validateAdd(command);
        validateManualSource(command.source());

        RequirementAddResult result = createOrEnsureManualRequirement(command);

        return toAddResponse(
                result.requirement(),
                result.source(),
                result.previousQuantity(),
                result.addedQuantity(),
                command.source()
        );
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse addRequirement(AddPurchaseRequirementCommand command) {

        validateAdd(command);

        RequirementAddResult result = createOrAccumulateRequirement(command);

        return purchaseRequirementMapper.toResponse(result.requirement());
    }

    @Transactional
    @Override
    public AddPurchaseRequirementResponse undoSource(Long requirementId, Long sourceId) {

        PurchaseRequirement requirement = getPendingRequirementForUpdate(requirementId);

        PurchaseRequirementSource source = sourceRepository.findByIdAndRequirementId(sourceId, requirementId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró la acción de reposición indicada."
                        )
                );

        validateUndoSource(source);

        return reverseSource(requirement, source);
    }

    @Transactional
    @Override
    public void undoAutomaticSource(
            PurchaseRequirementSourceType sourceType,
            String referenceId
    ) {
        if (sourceType == null || referenceId == null || referenceId.isBlank()) {
            return;
        }

        if (sourceType != PurchaseRequirementSourceType.SALE_ITEM) {
            throw new BusinessException("El origen indicado no admite reversión automática.");
        }

        PurchaseRequirementSource source = sourceRepository
                .findByTypeAndReferenceId(sourceType, referenceId)
                .orElse(null);

        if (source == null || sourceRepository.existsByReversedSourceId(source.getId())) {
            return;
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseRequirement requirement = requirementRepository
                .findByIdAndBookstoreIdForUpdate(
                        source.getRequirement().getId(),
                        bookstoreId
                )
                .orElse(null);

        if (requirement == null || requirement.getStatus() != PurchaseRequirementStatus.PENDING) {
            return;
        }

        int rawQuantityAfterReversal = Math.max(
                requirement.getQuantity() - source.getQuantity(),
                0
        );

        int orderedQuantityBeforeReversal = Math.toIntExact(
                purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(requirement.getId())
        );

        int draftQuantityToRelease = Math.min(
                source.getQuantity(),
                Math.max(orderedQuantityBeforeReversal - rawQuantityAfterReversal, 0)
        );

        if (draftQuantityToRelease > 0) {
            releaseDraftOrderAllocation(requirement.getId(), draftQuantityToRelease);
        }

        reverseAutomaticSource(requirement, source);
    }

    private void reverseAutomaticSource(
            PurchaseRequirement requirement,
            PurchaseRequirementSource source
    ) {
        int previousQuantity = requirement.getQuantity();
        int rawQuantity = previousQuantity - source.getQuantity();

        int orderedQuantity = Math.toIntExact(
                purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(requirement.getId())
        );

        int targetQuantity = Math.max(Math.max(rawQuantity, 0), orderedQuantity);

        PurchaseRequirementSource reversal = PurchaseRequirementSource.builder()
                .requirement(requirement)
                .type(PurchaseRequirementSourceType.REVERSAL)
                .quantity(-source.getQuantity())
                .reversedSource(source)
                .build();

        sourceRepository.save(reversal);

        int orderFloorAdjustment = targetQuantity - rawQuantity;

        if (orderFloorAdjustment > 0) {
            sourceRepository.save(
                    PurchaseRequirementSource.builder()
                            .requirement(requirement)
                            .type(PurchaseRequirementSourceType.ADJUSTMENT)
                            .quantity(orderFloorAdjustment)
                            .referenceId("ORDER_FLOOR:" + source.getId())
                            .build()
            );
        }

        if (rawQuantity <= 0) {
            // La necesidad que originó la venta desapareció. Si ya había unidades
            // comprometidas en pedidos enviados conservamos la cantidad histórica,
            // pero la necesidad deja de estar activa en Reponer.
            if (targetQuantity > 0) {
                requirement.setQuantity(targetQuantity);
            }
            requirement.setStatus(PurchaseRequirementStatus.CANCELLED);
        } else {
            requirement.setQuantity(targetQuantity);
        }
    }

    private void releaseDraftOrderAllocation(Long requirementId, int quantityToRelease) {
        int remaining = quantityToRelease;

        for (var item : purchaseOrderItemRepository.findDraftItemsByRequirementIdForUpdate(requirementId)) {
            if (remaining <= 0) {
                break;
            }

            int linkedQuantity = item.getRequirementQuantity() != null
                    ? item.getRequirementQuantity()
                    : 0;

            if (linkedQuantity <= 0) {
                continue;
            }

            int released = Math.min(linkedQuantity, remaining);
            int newLinkedQuantity = linkedQuantity - released;
            int newItemQuantity = item.getQuantity() - released;

            if (newItemQuantity <= 0) {
                purchaseOrderItemRepository.delete(item);
            } else {
                item.setQuantity(newItemQuantity);
                item.setRequirementQuantity(newLinkedQuantity);

                if (newLinkedQuantity == 0) {
                    item.setRequirement(null);
                }
            }

            remaining -= released;
        }

        purchaseOrderItemRepository.flush();
    }

    private AddPurchaseRequirementResponse reverseSource(
            PurchaseRequirement requirement,
            PurchaseRequirementSource source
    ) {
        int previousQuantity = requirement.getQuantity();
        int newQuantity = previousQuantity - source.getQuantity();

        if (newQuantity < 0) {
            throw new BusinessException("La acción no puede deshacerse porque dejaría una cantidad inválida.");
        }

        int orderedQuantity = Math.toIntExact(
                purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(requirement.getId())
        );

        if (newQuantity < orderedQuantity) {
            throw new BusinessException(
                    "La acción no puede deshacerse porque parte de esas unidades ya fue incorporada a pedidos."
            );
        }

        PurchaseRequirementSource reversal = PurchaseRequirementSource.builder()
                .requirement(requirement)
                .type(PurchaseRequirementSourceType.REVERSAL)
                .quantity(-source.getQuantity())
                .reversedSource(source)
                .build();

        sourceRepository.save(reversal);

        if (newQuantity == 0) {
            requirement.setStatus(PurchaseRequirementStatus.CANCELLED);
        } else {
            requirement.setQuantity(newQuantity);
        }

        return new AddPurchaseRequirementResponse(
                requirement.getId(),

                requirement.getBook().getId(),
                requirement.getBook().getPreferredIsbn(),
                requirement.getBook().getTitle(),
                requirement.getBook().getCoverUrl(),

                previousQuantity,
                -source.getQuantity(),
                newQuantity,

                reversal.getId(),
                PurchaseRequirementSourceType.REVERSAL,

                requirement.getPreferredProvider() != null
                        ? requirement.getPreferredProvider().getId()
                        : null,

                requirement.getPreferredProvider() != null
                        ? requirement.getPreferredProvider().getName()
                        : null,

                newQuantity > 0
                        ? getEffectiveReasons(requirement.getId())
                        : List.of()
        );
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse reactivate(Long requirementId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseRequirement requirement =
                requirementRepository
                        .findByIdAndBookstoreIdForUpdate(
                                requirementId,
                                bookstoreId
                        )
                        .orElseThrow(() -> new BusinessException(
                                "No se encontró la necesidad de compra solicitada.")
                        );

        if (requirement.getStatus() != PurchaseRequirementStatus.CANCELLED) {
            throw new BusinessException("Solo se puede reactivar una necesidad de compra cancelada.");
        }

        requirement.setStatus(PurchaseRequirementStatus.PENDING);

        return purchaseRequirementMapper.toResponse(requirement);
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse adjust(Long requirementId, Integer quantity) {

        if (quantity == null || quantity < 0) {
            throw new BusinessException("La cantidad no puede ser negativa.");
        }

        PurchaseRequirement requirement = getPendingRequirementForUpdate(requirementId);

        int orderedQuantity = Math.toIntExact(
                purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(requirement.getId())
        );

        if (quantity < orderedQuantity) {
            throw new BusinessException(
                    "La necesidad no puede quedar por debajo de las "
                            + orderedQuantity
                            + " unidades ya incorporadas a pedidos."
            );
        }

        int currentQuantity = requirement.getQuantity();

        if (quantity == currentQuantity) {
            return purchaseRequirementMapper.toResponse(requirement);
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

            return purchaseRequirementMapper.toResponse(requirement);
        }

        requirement.setQuantity(quantity);

        return purchaseRequirementMapper.toResponse(requirement);
    }

    @Transactional
    @Override
    public PurchaseRequirementResponse assignProvider(Long requirementId, Long providerId) {

        PurchaseRequirement requirement = getPendingRequirementForUpdate(requirementId);

        if (providerId == null) {

            requirement.setPreferredProvider(null);

            return purchaseRequirementMapper.toResponse(requirement);
        }

        Provider provider = resolveProvider(providerId, requirement.getBook().getId());

        requirement.setPreferredProvider(provider);

        return purchaseRequirementMapper.toResponse(requirement);
    }

    @Transactional
    @Override
    public void cancel(Long requirementId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseRequirement requirement =
                requirementRepository
                        .findByIdAndBookstoreIdForUpdate(
                                requirementId,
                                bookstoreId
                        )
                        .orElseThrow(() -> new BusinessException(
                                "No se encontró la necesidad de compra solicitada.")
                        );

        if (requirement.getStatus() == PurchaseRequirementStatus.CANCELLED) {
            throw new BusinessException("La necesidad de compra ya se encuentra cancelada.");
        }

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

        return purchaseRequirementMapper.toResponse(requirement);
    }

    @Override
    public BookPurchaseRequirementStatusResponse findBookStatus(Long bookId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseRequirement requirement = requirementRepository
                .findByBookstoreIdAndBookIdAndStatus(
                        bookstoreId,
                        bookId,
                        PurchaseRequirementStatus.PENDING
                )
                .orElse(null);

        if (requirement == null) {
            return BookPurchaseRequirementStatusResponse.notPending();
        }

        int orderedQuantity = Math.toIntExact(
                purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(requirement.getId())
        );

        int remainingQuantity = Math.max(requirement.getQuantity() - orderedQuantity, 0);

        Provider provider = requirement.getPreferredProvider();

        return new BookPurchaseRequirementStatusResponse(
                true,
                requirement.getId(),
                requirement.getQuantity(),
                orderedQuantity,
                remainingQuantity,
                provider != null ? provider.getId() : null,
                provider != null ? provider.getName() : null
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PurchaseRequirementSummaryResponse> findAll(PurchaseRequirementFilter filter, Pageable pageable) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

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

        List<PurchaseRequirement> requirements = page.getContent();

        List<Long> bookIds =
                requirements.stream()
                        .map(requirement -> requirement.getBook().getId())
                        .distinct()
                        .toList();

        List<Long> requirementIds = requirements.stream()
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
                        .findEffectiveGroupedReasons(requirementIds)
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

        Map<Long, Integer> orderedQuantityByRequirementId =
                purchaseOrderItemRepository
                        .findOrderedQuantitiesByRequirementIds(
                                requirementIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        PurchaseRequirementOrderedQuantityProjection::getRequirementId,
                                        projection ->
                                                Math.toIntExact(
                                                        projection.getOrderedQuantity()
                                                )
                                )
                        );

        Map<Long, List<PurchaseRequirementProviderResponse>>
                availableProvidersByBookId =
                providerBookRepository
                        .findAvailableProvidersByBookIds(bookIds, ProviderType.COMMERCIAL)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        PurchaseRequirementProviderProjection::getBookId,
                                        Collectors.mapping(
                                                provider ->
                                                        new PurchaseRequirementProviderResponse(
                                                                provider.getProviderId(),
                                                                provider.getProviderName(),
                                                                provider.getPrice()
                                                        ),
                                                Collectors.toList()
                                        )
                                )
                        );

        return page.map(requirement -> {

            Long bookId =
                    requirement.getBook().getId();

            Inventory inventory =
                    inventoryByBookId.get(bookId);

            List<PurchaseRequirementReasonResponse> reasons =
                    reasonsByRequirementId.getOrDefault(
                            requirement.getId(),
                            List.of()
                    );

            int orderedQuantity =
                    orderedQuantityByRequirementId.getOrDefault(
                            requirement.getId(),
                            0
                    );

            List<PurchaseRequirementProviderResponse> availableProviders =
                    availableProvidersByBookId.getOrDefault(
                            bookId,
                            List.of()
                    );

            return purchaseRequirementMapper.toSummaryResponse(
                    requirement,
                    inventory,
                    reasons,
                    availableProviders,
                    orderedQuantity
            );
        });
    }

    private AddPurchaseRequirementResponse toAddResponse(
            PurchaseRequirement requirement,
            PurchaseRequirementSource source,
            int previousQuantity,
            int addedQuantity,
            PurchaseRequirementSourceType requestedSource
    ) {

        Provider preferredProvider = requirement.getPreferredProvider();

        return new AddPurchaseRequirementResponse(
                requirement.getId(),

                requirement.getBook().getId(),
                requirement.getBook().getPreferredIsbn(),
                requirement.getBook().getTitle(),
                requirement.getBook().getCoverUrl(),

                previousQuantity,
                addedQuantity,
                requirement.getQuantity(),

                source != null ? source.getId() : null,
                source != null ? source.getType() : requestedSource,

                preferredProvider != null ? preferredProvider.getId() : null,
                preferredProvider != null ? preferredProvider.getName() : null,

                getEffectiveReasons(requirement.getId())
        );
    }

    private RequirementAddResult createOrEnsureManualRequirement(AddPurchaseRequirementCommand command) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Book book = bookService.getEntityById(command.bookId());
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);
        Provider provider = resolveProvider(command.providerId(), command.bookId());

        PurchaseRequirement requirement = requirementRepository
                .findByBookstoreAndBookAndStatusForUpdate(
                        bookstoreId,
                        command.bookId(),
                        PurchaseRequirementStatus.PENDING
                )
                .orElse(null);

        int previousQuantity = requirement != null ? requirement.getQuantity() : 0;

        if (requirement == null) {
            requirement = PurchaseRequirement.builder()
                    .bookstore(bookstore)
                    .book(book)
                    .quantity(command.quantity())
                    .preferredProvider(provider)
                    .status(PurchaseRequirementStatus.PENDING)
                    .build();
        } else {
            requirement.setQuantity(Math.max(previousQuantity, command.quantity()));

            if (requirement.getPreferredProvider() == null && provider != null) {
                requirement.setPreferredProvider(provider);
            }
        }

        requirement = requirementRepository.save(requirement);

        int addedQuantity = requirement.getQuantity() - previousQuantity;
        PurchaseRequirementSource source = null;

        if (addedQuantity > 0) {
            source = sourceRepository.save(
                    PurchaseRequirementSource.builder()
                            .requirement(requirement)
                            .type(command.source())
                            .quantity(addedQuantity)
                            .referenceId(command.referenceId())
                            .provider(provider)
                            .build()
            );
        }

        return new RequirementAddResult(requirement, source, previousQuantity, addedQuantity);
    }

    private RequirementAddResult createOrAccumulateRequirement(AddPurchaseRequirementCommand command) {

        if (command.referenceId() != null && !command.referenceId().isBlank()) {
            PurchaseRequirementSource existingSource = sourceRepository
                    .findByTypeAndReferenceId(command.source(), command.referenceId())
                    .orElse(null);

            if (existingSource != null) {
                PurchaseRequirement existingRequirement = existingSource.getRequirement();
                return new RequirementAddResult(
                        existingRequirement,
                        existingSource,
                        Math.max(existingRequirement.getQuantity() - existingSource.getQuantity(), 0),
                        0
                );
            }
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Book book = bookService.getEntityById(command.bookId());

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        Provider provider = resolveProvider(command.providerId(), command.bookId());

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

        int previousQuantity = requirement.getQuantity();

        requirement.setQuantity(previousQuantity + command.quantity());

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

        source = sourceRepository.save(source);

        return new RequirementAddResult(requirement, source, previousQuantity, command.quantity());
    }

    private List<PurchaseRequirementReasonResponse> getEffectiveReasons(
            Long requirementId
    ) {

        return sourceRepository
                .findEffectiveGroupedReasons(
                        List.of(requirementId)
                )
                .stream()
                .map(reason ->
                        new PurchaseRequirementReasonResponse(
                                reason.getType(),
                                Math.toIntExact(
                                        reason.getQuantity()
                                )
                        )
                )
                .toList();
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

    private Provider resolveProvider(
            Long providerId,
            Long bookId
    ) {

        if (providerId == null) {
            return null;
        }

        Provider provider =
                providerRepository
                        .findById(providerId)
                        .filter(Provider::isPurchasable)
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

        if ((command.source() == PurchaseRequirementSourceType.SALE
                || command.source() == PurchaseRequirementSourceType.SALE_ITEM)
                && (command.referenceId() == null || command.referenceId().isBlank())
        ) {
            throw new BusinessException("Una reposición originada por una venta debe indicar su referencia.");
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

    private void validateUndoSource(PurchaseRequirementSource source) {

        if (
                source.getType()
                        != PurchaseRequirementSourceType.INVENTORY
                        && source.getType()
                        != PurchaseRequirementSourceType.CATALOG
                        && source.getType()
                        != PurchaseRequirementSourceType.MANUAL
        ) {
            throw new BusinessException("Esta acción no puede deshacerse manualmente.");
        }

        if (sourceRepository.existsByReversedSourceId(source.getId())) {
            throw new BusinessException("La acción ya fue deshecha.");
        }
    }

    private record RequirementAddResult(
            PurchaseRequirement requirement,
            PurchaseRequirementSource source,
            int previousQuantity,
            int addedQuantity
    ) {
    }
}