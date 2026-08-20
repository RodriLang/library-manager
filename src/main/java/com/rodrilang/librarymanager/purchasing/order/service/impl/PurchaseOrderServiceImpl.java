package com.rodrilang.librarymanager.purchasing.order.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.purchasing.order.dto.PurchaseOrderFilter;
import com.rodrilang.librarymanager.purchasing.order.dto.request.AddPurchaseOrderItemRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.CreatePurchaseOrderRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.CreatePurchaseOrdersFromRequirementsRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.UpdatePurchaseOrderItemRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.response.CreatePurchaseOrdersFromRequirementsResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PreparedPurchaseOrderResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderDetailResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderItemResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderResponse;
import com.rodrilang.librarymanager.purchasing.order.mapper.PurchaseOrderMapper;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrder;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderItemRepository;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderRepository;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderSpecifications;
import com.rodrilang.librarymanager.purchasing.order.repository.projection.PurchaseOrderTotalsProjection;
import com.rodrilang.librarymanager.purchasing.order.repository.projection.PurchaseRequirementOrderedQuantityProjection;
import com.rodrilang.librarymanager.purchasing.order.service.PurchaseOrderService;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementRepository;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;

    private final PurchaseRequirementRepository requirementRepository;

    private final PriceListProviderRepository providerRepository;
    private final ProviderBookRepository providerBookRepository;
    private final EditorialPriceRepository editorialPriceRepository;

    private final PurchaseOrderMapper mapper;

    private final BookService bookService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    @Transactional
    @Override
    public PurchaseOrderDetailResponse create(CreatePurchaseOrderRequest request) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);
        PriceListProvider provider = getActiveProvider(request.providerId());

        Optional<PurchaseOrder> existing =
                orderRepository.findDraftByBookstoreIdAndProviderIdForUpdate(bookstoreId, provider.getId());

        if (existing.isPresent()) {
            throw new BusinessException(
                    "Ya existe un pedido en borrador para el distribuidor \"" + provider.getName() + "\"."
            );
        }

        PurchaseOrder order = PurchaseOrder.builder()
                .bookstore(bookstore)
                .provider(provider)
                .orderNumber("TMP-" + UUID.randomUUID())
                .status(PurchaseOrderStatus.DRAFT)
                .notes(normalizeNullableText(request.notes()))
                .build();

        order = orderRepository.saveAndFlush(order);
        order.setOrderNumber(generateOrderNumber(order.getId()));

        return getDetailResponse(order);
    }

    @Transactional
    @Override
    public CreatePurchaseOrdersFromRequirementsResponse createFromRequirements(CreatePurchaseOrdersFromRequirementsRequest request) {

        if (request == null || request.requirementIds() == null || request.requirementIds().isEmpty()) {
            throw new BusinessException("Debe seleccionar al menos una necesidad de compra.");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        List<Long> requirementIds = request.requirementIds().stream()
                .distinct()
                .toList();

        List<PurchaseRequirement> requirements =
                requirementRepository.findAllByIdsAndBookstoreIdForUpdate(requirementIds, bookstoreId);

        if (requirements.size() != requirementIds.size()) {
            throw new BusinessException(
                    "Una o más necesidades de compra no existen o no pertenecen a la librería actual."
            );
        }

        validateRequirementsForPreparation(requirements);

        Map<Long, Integer> orderedQuantityByRequirementId =
                itemRepository
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

        List<RequirementPreparation> preparations = new ArrayList<>();

        for (PurchaseRequirement requirement : requirements) {

            int orderedQuantity = orderedQuantityByRequirementId.getOrDefault(requirement.getId(), 0);

            int remainingQuantity = requirement.getQuantity() - orderedQuantity;

            if (remainingQuantity <= 0) {
                throw new BusinessException(
                        "La necesidad de compra para \""
                                + requirement.getBook().getTitle()
                                + "\" ya se encuentra cubierta por pedidos."
                );
            }

            validateProviderBook(requirement.getPreferredProvider().getId(), requirement.getBook().getId());

            preparations.add(new RequirementPreparation(requirement, remainingQuantity));
        }

        Map<Long, List<RequirementPreparation>> preparationsByProvider =
                preparations.stream()
                        .collect(
                                Collectors.groupingBy(
                                        preparation ->
                                                preparation.requirement()
                                                        .getPreferredProvider()
                                                        .getId(),
                                        LinkedHashMap::new,
                                        Collectors.toList()
                                )
                        );

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        List<PreparedPurchaseOrderResponse> preparedOrders = new ArrayList<>();

        for (List<RequirementPreparation> providerPreparations : preparationsByProvider.values()) {

            PurchaseRequirement firstRequirement = providerPreparations.getFirst().requirement();

            PriceListProvider provider = getActiveProvider(firstRequirement.getPreferredProvider().getId());

            DraftOrderResolution resolution = resolveDraftOrder(bookstore, provider);
            PurchaseOrder order = resolution.order();

            for (RequirementPreparation preparation : providerPreparations) {
                addRequirementToDraft(order, preparation.requirement(), preparation.quantity(), provider);
            }

            List<PurchaseOrderItem> orderItems = itemRepository.findAllByPurchaseOrderIdOrderByIdAsc(order.getId());

            int totalUnits = orderItems.stream()
                    .mapToInt(PurchaseOrderItem::getQuantity)
                    .sum();

            BigDecimal estimatedTotal = calculateEstimatedTotal(orderItems);

            preparedOrders.add(
                    new PreparedPurchaseOrderResponse(
                            order.getId(),
                            order.getOrderNumber(),
                            provider.getId(),
                            provider.getName(),
                            orderItems.size(),
                            totalUnits,
                            estimatedTotal,
                            resolution.created()
                    )
            );
        }

        return new CreatePurchaseOrdersFromRequirementsResponse(
                preparedOrders
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PurchaseOrderResponse> findAll(
            PurchaseOrderFilter filter,
            Pageable pageable
    ) {

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        Specification<PurchaseOrder> specification =
                Specification.allOf(
                        PurchaseOrderSpecifications.bookstoreId(bookstoreId),
                        PurchaseOrderSpecifications.search(filter.query()),
                        PurchaseOrderSpecifications.providerId(filter.providerId()),
                        PurchaseOrderSpecifications.status(filter.status())
                );

        Page<PurchaseOrder> page = orderRepository.findAll(specification, pageable);

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> orderIds =
                page.getContent()
                        .stream()
                        .map(PurchaseOrder::getId)
                        .toList();

        Map<Long, PurchaseOrderTotalsProjection> totalsByOrderId =
                itemRepository
                        .findTotalsByOrderIds(orderIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        PurchaseOrderTotalsProjection::getOrderId,
                                        Function.identity()
                                )
                        );

        return page.map(order -> {

            PurchaseOrderTotalsProjection totals =
                    totalsByOrderId.get(
                            order.getId()
                    );

            return new PurchaseOrderResponse(
                    order.getId(),
                    order.getOrderNumber(),

                    order.getProvider().getId(),
                    order.getProvider().getName(),

                    order.getStatus(),

                    totals != null ? Math.toIntExact(totals.getItemCount()) : 0,

                    totals != null ? Math.toIntExact(totals.getTotalUnits()) : 0,

                    totals != null ? totals.getEstimatedTotal() : null,

                    order.getCreatedAt(),
                    order.getSentAt()
            );
        });
    }

    @Transactional(readOnly = true)
    @Override
    public PurchaseOrderDetailResponse findById(Long orderId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = orderRepository.findByIdAndBookstoreId(orderId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido con ID: " + orderId));

        return getDetailResponse(order);
    }

    @Transactional
    @Override
    public PurchaseOrderDetailResponse addItem(Long orderId, AddPurchaseOrderItemRequest request) {

        validateAddItemRequest(request);

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = getDraftOrderForUpdate(orderId, bookstoreId);

        ResolvedOrderBook resolved = resolveBook(bookstoreId, request);

        Book book = resolved.book();

        PurchaseRequirement requirement = resolved.requirement();

        validateProviderBook(order.getProvider().getId(), book.getId());

        PurchaseOrderItem existingItem = itemRepository.findByPurchaseOrderIdAndBookIdForUpdate(order.getId(), book.getId())
                .orElse(null);

        if (existingItem != null) {

            handleExistingItem(existingItem, requirement, request.quantity());

        } else {

            int requirementQuantity = calculateRequirementQuantity(requirement, request.quantity());

            BigDecimal unitPrice = findCurrentPrice(order.getProvider().getId(), book.getId());

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(order)
                    .book(book)
                    .requirement(requirementQuantity > 0 ? requirement : null)
                    .quantity(request.quantity())
                    .requirementQuantity(requirementQuantity)
                    .unitPrice(unitPrice)
                    .build();

            itemRepository.save(item);
        }

        return getDetailResponse(order);
    }

    @Transactional
    @Override
    public PurchaseOrderDetailResponse updateItem(Long orderId, Long itemId, UpdatePurchaseOrderItemRequest request) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = getDraftOrderForUpdate(orderId, bookstoreId);

        PurchaseOrderItem item = getOrderItem(order.getId(), itemId);

        int requirementQuantity = calculateUpdatedRequirementQuantity(item, request.quantity());

        item.setQuantity(request.quantity());

        item.setRequirementQuantity(requirementQuantity);

        if (requirementQuantity == 0) {
            item.setRequirement(null);
        }

        return getDetailResponse(order);
    }

    @Transactional
    @Override
    public PurchaseOrderDetailResponse removeItem(Long orderId, Long itemId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = getDraftOrderForUpdate(orderId, bookstoreId);

        PurchaseOrderItem item = getOrderItem(order.getId(), itemId);

        itemRepository.delete(item);

        itemRepository.flush();

        return getDetailResponse(order);
    }

    @Transactional
    @Override
    public PurchaseOrderDetailResponse send(Long orderId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = getDraftOrderForUpdate(orderId, bookstoreId);

        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderIdOrderByIdAsc(order.getId());

        if (items.isEmpty()) {
            throw new BusinessException("No se puede enviar un pedido sin libros.");
        }

        order.setStatus(PurchaseOrderStatus.SENT);

        order.setSentAt(Instant.now());

        return buildDetailResponse(order, items);
    }

    @Transactional
    @Override
    public void cancel(Long orderId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        PurchaseOrder order = orderRepository.findByIdAndBookstoreIdForUpdate(orderId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido con ID: " + orderId));

        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessException("El pedido ya se encuentra cancelado.");
        }

        order.setStatus(PurchaseOrderStatus.CANCELLED);
    }

    private ResolvedOrderBook resolveBook(Long bookstoreId, AddPurchaseOrderItemRequest request) {

        boolean hasRequirement = request.requirementId() != null;

        boolean hasBook = request.bookId() != null;

        /*
         * XOR:
         *
         * false / false → inválido
         * true / true   → inválido
         * true / false  → válido
         * false / true  → válido
         */
        if (hasRequirement == hasBook) {
            throw new BusinessException("Debe indicar requirementId o bookId, pero no ambos.");
        }

        if (hasRequirement) {

            PurchaseRequirement requirement =
                    requirementRepository
                            .findByIdAndBookstoreId(
                                    request.requirementId(),
                                    bookstoreId
                            )
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "No se encontró la necesidad de compra con ID: "
                                                    + request.requirementId()
                                    )
                            );

            if (requirement.getStatus() != PurchaseRequirementStatus.PENDING) {
                throw new BusinessException("La necesidad de compra seleccionada ya no está pendiente.");
            }

            return new ResolvedOrderBook(requirement.getBook(), requirement);
        }

        Book book = bookService.getEntityById(request.bookId());

        PurchaseRequirement requirement =
                requirementRepository
                        .findByBookstoreIdAndBookIdAndStatus(
                                bookstoreId,
                                book.getId(),
                                PurchaseRequirementStatus.PENDING
                        )
                        .orElse(null);

        return new ResolvedOrderBook(book, requirement);
    }

    private void handleExistingItem(PurchaseOrderItem item, PurchaseRequirement requirement, int addedQuantity) {

        if (item.getRequirement() != null
                && requirement != null
                && !item.getRequirement()
                .getId()
                .equals(requirement.getId())
        ) {
            throw new BusinessException("El libro ya se encuentra vinculado a otra necesidad de compra.");
        }

        PurchaseRequirement effectiveRequirement =
                item.getRequirement() != null
                        ? item.getRequirement()
                        : requirement;

        int currentRequirementQuantity =
                item.getRequirementQuantity() != null
                        ? item.getRequirementQuantity()
                        : 0;

        int additionalRequirementQuantity =
                calculateAdditionalRequirementQuantity(
                        effectiveRequirement,
                        addedQuantity,
                        currentRequirementQuantity
                );

        item.setQuantity(item.getQuantity() + addedQuantity);

        item.setRequirementQuantity(currentRequirementQuantity + additionalRequirementQuantity);

        if (item.getRequirement() == null && additionalRequirementQuantity > 0) {
            item.setRequirement(effectiveRequirement);
        }
    }

    private int calculateAdditionalRequirementQuantity(
            PurchaseRequirement requirement,
            int addedQuantity,
            int currentItemRequirementQuantity
    ) {

        if (requirement == null) {
            return 0;
        }

        long alreadyOrdered = itemRepository.sumOrderedQuantityByRequirementId(requirement.getId());

        long orderedByOtherItems = alreadyOrdered - currentItemRequirementQuantity;

        long remainingForThisRequirement =
                Math.max(
                        requirement.getQuantity()
                                - orderedByOtherItems
                                - currentItemRequirementQuantity,
                        0
                );

        return (int) Math.min(addedQuantity, remainingForThisRequirement);
    }

    private int calculateRequirementQuantity(PurchaseRequirement requirement, int requestedQuantity) {

        if (requirement == null) {
            return 0;
        }

        long alreadyOrdered = itemRepository.sumOrderedQuantityByRequirementId(requirement.getId());

        long remaining = Math.max(requirement.getQuantity() - alreadyOrdered, 0);

        return (int) Math.min(requestedQuantity, remaining);
    }

    private int calculateUpdatedRequirementQuantity(PurchaseOrderItem item, int newQuantity) {

        if (item.getRequirement() == null) {
            return 0;
        }

        Long orderedQuantity = itemRepository.sumOrderedQuantityByRequirementId(item.getRequirement().getId());

        long orderedByOtherItems = orderedQuantity - item.getRequirementQuantity();
        long remaining = Math.max(item.getRequirement().getQuantity() - orderedByOtherItems, 0);

        return (int) Math.min(newQuantity, remaining);
    }

    private void validateProviderBook(Long providerId, Long bookId) {

        boolean available = providerBookRepository.existsByProviderIdAndBookIdAndActiveTrue(providerId, bookId);

        if (!available) {
            throw new BusinessException("El proveedor del pedido no comercializa este libro.");
        }
    }

    private BigDecimal findCurrentPrice(Long providerId, Long bookId) {

        return editorialPriceRepository
                .findFirstByBookIdAndProviderIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                        bookId,
                        providerId,
                        LocalDate.now(ZoneId.systemDefault())
                )
                .map(EditorialPrice::getPrice)
                .orElse(null);
    }

    private PriceListProvider getActiveProvider(Long providerId) {

        return providerRepository.findById(providerId)
                .filter(PriceListProvider::isActive)
                .orElseThrow(() -> new BusinessException("El proveedor seleccionado no se encuentra activo."));
    }

    private PurchaseOrder getDraftOrderForUpdate(Long orderId, Long bookstoreId) {

        return orderRepository
                .findByIdAndBookstoreIdAndStatusForUpdate(
                        orderId,
                        bookstoreId,
                        PurchaseOrderStatus.DRAFT
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró un pedido en borrador con ID: "
                                        + orderId
                        )
                );
    }

    private PurchaseOrderItem getOrderItem(Long orderId, Long itemId) {

        return itemRepository.findByIdAndPurchaseOrderId(itemId, orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró el ítem "
                                        + itemId
                                        + " dentro del pedido "
                                        + orderId
                        )
                );
    }

    private PurchaseOrderDetailResponse getDetailResponse(PurchaseOrder order) {

        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderIdOrderByIdAsc(order.getId());

        return buildDetailResponse(order, items);
    }

    private PurchaseOrderDetailResponse buildDetailResponse(PurchaseOrder order, List<PurchaseOrderItem> items) {

        List<PurchaseOrderItemResponse> itemResponses = items.stream()
                .map(mapper::toItemResponse)
                .toList();

        int totalUnits = items.stream()
                .mapToInt(PurchaseOrderItem::getQuantity)
                .sum();

        BigDecimal estimatedTotal = calculateEstimatedTotal(items);

        return new PurchaseOrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getProvider().getId(),
                order.getProvider().getName(),
                order.getStatus(),
                order.getNotes(),
                items.size(),
                totalUnits,
                estimatedTotal,
                order.getCreatedAt(),
                order.getSentAt(),
                itemResponses
        );
    }

    private DraftOrderResolution resolveDraftOrder(Bookstore bookstore, PriceListProvider provider) {

        Optional<PurchaseOrder> existing =
                orderRepository.findDraftByBookstoreIdAndProviderIdForUpdate(bookstore.getId(), provider.getId());

        if (existing.isPresent()) {
            return new DraftOrderResolution(existing.get(), false);
        }

        return new DraftOrderResolution(createDraftOrder(bookstore, provider), true);
    }

    private PurchaseOrder createDraftOrder(Bookstore bookstore, PriceListProvider provider) {

        PurchaseOrder order = PurchaseOrder.builder()
                .bookstore(bookstore)
                .provider(provider)
                .orderNumber("TMP-" + UUID.randomUUID())
                .status(PurchaseOrderStatus.DRAFT)
                .build();

        order = orderRepository.saveAndFlush(order);
        order.setOrderNumber(generateOrderNumber(order.getId()));

        return order;
    }

    private void addRequirementToDraft(
            PurchaseOrder order,
            PurchaseRequirement requirement,
            int quantity,
            PriceListProvider provider
    ) {
        Book book = requirement.getBook();

        PurchaseOrderItem item =
                itemRepository.findByPurchaseOrderIdAndBookIdForUpdate(order.getId(), book.getId())
                        .orElse(null);

        if (item != null) {
            handleExistingItem(item, requirement, quantity);
            item.setUnitPrice(findCurrentPrice(provider.getId(), book.getId()));
            return;
        }

        itemRepository.save(
                PurchaseOrderItem.builder()
                        .purchaseOrder(order)
                        .book(book)
                        .requirement(requirement)
                        .quantity(quantity)
                        .requirementQuantity(quantity)
                        .unitPrice(findCurrentPrice(provider.getId(), book.getId()))
                        .build()
        );
    }

    private void validateAddItemRequest(AddPurchaseOrderItemRequest request) {

        if (request == null) {
            throw new BusinessException("Debe especificarse el ítem del pedido.");
        }

        if (request.quantity() <= 0) {
            throw new BusinessException("La cantidad debe ser mayor a cero.");
        }
    }

    private void validateRequirementsForPreparation(List<PurchaseRequirement> requirements) {

        for (PurchaseRequirement requirement : requirements) {

            if (requirement.getStatus() != PurchaseRequirementStatus.PENDING) {
                throw new BusinessException(
                        "La necesidad de compra para \""
                                + requirement.getBook().getTitle()
                                + "\" ya no se encuentra pendiente."
                );
            }

            if (requirement.getPreferredProvider() == null) {
                throw new BusinessException(
                        "Debe asignar un distribuidor para \""
                                + requirement.getBook().getTitle()
                                + "\" antes de preparar el pedido."
                );
            }
        }
    }

    private BigDecimal calculateEstimatedTotal(List<PurchaseOrderItem> items) {

        boolean hasAnyPrice = items.stream()
                .anyMatch(item -> item.getUnitPrice() != null);

        if (!hasAnyPrice) {
            return null;
        }

        return items.stream()
                .filter(item -> item.getUnitPrice() != null)
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generateOrderNumber(Long orderId) {

        return "PED-%d-%06d".formatted(Year.now(ZoneId.systemDefault()).getValue(), orderId);
    }

    private String normalizeNullableText(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private record ResolvedOrderBook(
            Book book,
            PurchaseRequirement requirement
    ) {
    }

    private record RequirementPreparation(
            PurchaseRequirement requirement,
            int quantity
    ) {
    }

    private record DraftOrderResolution(
            PurchaseOrder order,
            boolean created
    ) {
    }
}