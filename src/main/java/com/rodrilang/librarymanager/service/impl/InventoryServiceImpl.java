package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductSyncService;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeResult;
import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.InventorySaleRequest;
import com.rodrilang.librarymanager.dto.request.ReactivateInventoryRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.BookProviderResponse;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookService;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePublicationRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementRepository;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.mapper.InventoryMapper;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;
import com.rodrilang.librarymanager.purchasing.requirement.service.PurchaseRequirementService;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.BookstoreService;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import com.rodrilang.librarymanager.service.InventoryService;
import com.rodrilang.librarymanager.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMapper inventoryMapper;
    private final BookService bookService;
    private final TiendanubeVariantSyncService tiendanubeVariantSyncService;
    private final TiendanubeProductSyncService tiendanubeProductSyncService;
    private final EditorialPriceService editorialPriceService;
    private final InventoryStockService inventoryStockService;
    private final PurchaseRequirementService purchaseRequirementService;
    private final BookstoreService bookstoreService;
    private final ProviderBookService providerBookService;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<String, String> INVENTORY_SORT_MAPPING = Map.of(
            "title", "book.titleSort",
            "salePrice", "salePrice"
    );

    @Transactional
    @Override
    public InventoryDetailResponse addToInventory(Long bookId, AddBookToInventoryRequest request) {
        Book book = bookService.getEntityById(bookId);
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        BookCondition condition = request.condition() != null ? request.condition() : BookCondition.NEW;

        if (inventoryRepository.existsByBookIdAndBookstoreIdAndCondition(bookId, bookstoreId, condition)) {
            throw new DuplicateResourceException(String.format(
                    "El libro ISBN: %s ya se encuentra registrado en el inventario como %s",
                    book.getPreferredIsbn(),
                    condition
            ));
        }

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        TiendanubeInventoryStatus tiendanubeStatus = Boolean.TRUE.equals(request.publishOnTiendanube())
                ? TiendanubeInventoryStatus.PENDING_PUBLICATION
                : TiendanubeInventoryStatus.NOT_PUBLISHED;

        boolean editorialPriceSyncEnabled =
                condition == BookCondition.NEW
                        && Boolean.TRUE.equals(request.editorialPriceSyncEnabled());

        Inventory inventory = Inventory.builder()
                .book(book)
                .bookstore(bookstore)
                .condition(condition)
                .stock(0)
                .minimumStock(request.minimumStock() != null ? request.minimumStock() : 0)
                .salePrice(request.salePrice())
                .editorialPriceSyncEnabled(editorialPriceSyncEnabled)
                .tiendanubePriceSyncEnabled(Boolean.TRUE.equals(request.tiendanubePriceSyncEnabled()))
                .tiendanubeStatus(tiendanubeStatus)
                .active(true)
                .build();

        Inventory saved = inventoryRepository.save(inventory);

        if (request.initialStock() > 0) {

            InventoryStockChangeResult result =
                    inventoryStockService.changeStock(
                            saved.getId(),
                            new InventoryStockChangeCommand(
                                    request.initialStock(),
                                    InventoryMovementType.INITIAL_STOCK,
                                    InventoryMovementSource.MANUAL,
                                    null,
                                    null,
                                    "Stock informado al agregar el libro al inventario"
                            )
                    );

            saved = result.inventory();
        }

        if (saved.getTiendanubeStatus() == TiendanubeInventoryStatus.PENDING_PUBLICATION) {
            eventPublisher.publishEvent(new TiendanubePublicationRequestedEvent(saved.getId()));
        }

        return toDetailResponse(saved);
    }

    @Transactional
    @Override
    public InventoryDetailResponse addStock(
            Long inventoryId,
            InventoryQuantityRequest request
    ) {
        Inventory inventory = getEntityById(inventoryId);

        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException(
                    "El inventario se encuentra inactivo. " +
                            "Debe reactivarse antes de agregar stock."
            );
        }

        InventoryStockChangeResult result =
                inventoryStockService.changeStock(
                        inventory.getId(),
                        new InventoryStockChangeCommand(
                                request.quantity(),
                                InventoryMovementType.ENTRY,
                                InventoryMovementSource.MANUAL,
                                null,
                                null,
                                null
                        )
                );

        return syncStockAndMap(
                result.inventory()
        );
    }

    @Transactional
    @Override
    public InventoryDetailResponse recordSale(
            Long inventoryId,
            InventorySaleRequest request
    ) {

        Inventory inventory = getEntityById(inventoryId);

        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException(
                    "El libro se encuentra inactivo en el inventario."
            );
        }

        InventoryStockChangeResult result =
                inventoryStockService.changeStock(
                        inventory.getId(),
                        new InventoryStockChangeCommand(
                                -request.quantity(),
                                InventoryMovementType.SALE,
                                InventoryMovementSource.MANUAL,
                                null,
                                null,
                                null
                        )
                );

        if (Boolean.TRUE.equals(request.replenish())) {

            purchaseRequirementService.addRequirement(
                    new AddPurchaseRequirementCommand(
                            inventory.getBook().getId(),
                            request.quantity(),
                            PurchaseRequirementSourceType.SALE,
                            result.movement().getId().toString(),
                            null
                    )
            );
        }

        return syncStockAndMap(
                result.inventory()
        );
    }

    @Override
    @Transactional
    public InventoryDetailResponse reactivate(Long inventoryId, ReactivateInventoryRequest request) {
        Inventory inventory = getEntityById(inventoryId);

        if (Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException("El inventario ya se encuentra activo.");
        }

        inventory.setActive(true);
        inventory.setSalePrice(request.salePrice());
        inventory.setMinimumStock(request.minimumStock() != null ? request.minimumStock() : 0);
        inventory.setCondition(request.condition() != null ? request.condition() : BookCondition.NEW);
        inventory.setEditorialPriceSyncEnabled(
                inventory.getCondition() == BookCondition.NEW
                        && Boolean.TRUE.equals(request.editorialPriceSyncEnabled())
        );
        inventory.setTiendanubePriceSyncEnabled(Boolean.TRUE.equals(request.tiendanubePriceSyncEnabled()));

        if (inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.NOT_PUBLISHED
                && Boolean.TRUE.equals(request.publishOnTiendanube())) {
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        }

        Inventory adjusted =
                inventoryStockService.adjustStockTo(
                        inventory.getId(),
                        request.stock(),
                        InventoryMovementSource.MANUAL,
                        "Stock informado al reactivar el inventario"
                );

        if (adjusted.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED) {
            tiendanubeVariantSyncService.syncStock(adjusted.getId(), adjusted.getStock());

            if (Boolean.TRUE.equals(adjusted.getTiendanubePriceSyncEnabled())) {
                tiendanubeVariantSyncService.syncPrice(adjusted.getId());
            }
        }

        return toDetailResponse(adjusted);
    }

    @Transactional
    @Override
    public InventoryDetailResponse update(Long inventoryId, UpdateInventoryRequest request) {
        Inventory inventory = getEntityById(inventoryId);

        BigDecimal previousSalePrice = inventory.getSalePrice();
        Boolean previousPriceSyncEnabled = inventory.getTiendanubePriceSyncEnabled();

        inventoryMapper.updateEntity(request, inventory);

        boolean priceChanged = !Objects.equals(previousSalePrice, inventory.getSalePrice());

        boolean priceSyncEnabled = Boolean.TRUE.equals(inventory.getTiendanubePriceSyncEnabled());

        boolean priceSyncJustEnabled = !Boolean.TRUE.equals(previousPriceSyncEnabled) && priceSyncEnabled;

        if (request.updateTiendaNube()) {

            eventPublisher.publishEvent(
                    new TiendanubeSyncRequestedEvent(
                            inventory.getId(),
                            TiendanubeSyncType.PUBLICATION
                    )
            );

        } else if (priceSyncEnabled && (priceChanged || priceSyncJustEnabled)) {

            eventPublisher.publishEvent(
                    new TiendanubeSyncRequestedEvent(
                            inventory.getId(),
                            TiendanubeSyncType.PRICE
                    )
            );
        }

        return toDetailResponse(inventory);
    }

    @Override
    public InventoryDetailResponse getById(Long inventoryId) {

        log.info("Buscando inventario con ID: {} en el inventario", inventoryId);
        Inventory inventory = getEntityById(inventoryId);

        return toDetailResponse(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDetailResponse getByBookId(Long bookId) {

        Inventory inventory =
                getEntityByBookIdAndCondition(
                        bookId,
                        BookCondition.NEW
                );

        return toDetailResponse(inventory);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InventorySummaryResponse> getAll(Pageable pageable) {

        pageable = PageableUtils.mapSortProperties(pageable, INVENTORY_SORT_MAPPING);

        return inventoryRepository.findAllByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId(), pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InventorySummaryResponse> search(String query, boolean force, Pageable pageable) {
        if (query == null || query.isBlank()) {
            Pageable normalizedPageable = PageableUtils.mapSortProperties(
                    pageable,
                    INVENTORY_SORT_MAPPING
            );

            return inventoryRepository
                    .findAllByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId(), normalizedPageable)
                    .map(this::toSummaryResponse);
        }

        String normalizedQuery = query.trim();

        boolean identifierQuery =
                normalizedQuery.matches("[0-9Xx\\-\\s]+");

        if (!force) {
            int minimumLength = identifierQuery ? 8 : 3;

            if (normalizedQuery.length() < minimumLength) {
                return Page.empty(pageable);
            }
        }

        Page<Inventory> inventory;

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        if (identifierQuery) {
            String normalizedIdentifier =
                    normalizeSearchIdentifier(normalizedQuery);

            if (normalizedIdentifier == null) {
                return Page.empty(pageable);
            }

            inventory = inventoryRepository.searchByIsbn(
                    bookstoreId,
                    normalizedIdentifier,
                    pageable
            );
        } else {
            inventory = inventoryRepository.searchText(
                    bookstoreId,
                    normalizedQuery,
                    pageable
            );
        }

        return inventory.map(this::toSummaryResponse);
    }

    @Transactional
    @Override
    public void deactivate(Long inventoryId) {

        Inventory inventory = getEntityById(inventoryId);

        inventory.setActive(false);

        if (inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED) {
            tiendanubeVariantSyncService.syncStock(inventory.getId(), 0);
        }
    }

    @Override
    @Transactional
    public void recordTiendanubeSale(Long inventoryId, Integer quantity, String orderId) {
        validateQuantity(quantity);

        inventoryStockService.changeStock(
                inventoryId,
                new InventoryStockChangeCommand(
                        -quantity,
                        InventoryMovementType.SALE,
                        InventoryMovementSource.TIENDANUBE,
                        InventoryMovementReferenceType.TIENDANUBE_ORDER,
                        orderId,
                        null
                )
        );
    }

    @Override
    @Transactional
    public void restoreTiendanubeCancelledOrderStock(Long inventoryId, Integer quantity, String orderId) {
        validateQuantity(quantity);

        boolean saleExists =
                inventoryMovementRepository
                        .existsByInventoryIdAndTypeAndReferenceTypeAndReferenceId(
                                inventoryId,
                                InventoryMovementType.SALE,
                                InventoryMovementReferenceType.TIENDANUBE_ORDER,
                                orderId
                        );

        if (!saleExists) {
            throw new BusinessException(
                    "No existe una venta TiendaNube registrada para la orden: "
                            + orderId
            );
        }

        boolean returnExists =
                inventoryMovementRepository
                        .existsByInventoryIdAndTypeAndReferenceTypeAndReferenceId(
                                inventoryId,
                                InventoryMovementType.RETURN,
                                InventoryMovementReferenceType.TIENDANUBE_ORDER,
                                orderId
                        );

        if (returnExists) {
            throw new BusinessException(
                    "El stock de la orden TiendaNube ya fue restaurado: " + orderId
            );
        }

        inventoryStockService.changeStock(
                inventoryId,
                new InventoryStockChangeCommand(
                        quantity,
                        InventoryMovementType.RETURN,
                        InventoryMovementSource.TIENDANUBE,
                        InventoryMovementReferenceType.TIENDANUBE_ORDER,
                        orderId,
                        "Stock restaurado por cancelación del pedido"
                )
        );
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("La cantidad debe ser mayor a cero");
        }
    }

    private Inventory getEntityById(Long inventoryId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return inventoryRepository
                .findByIdAndBookstoreId(
                        inventoryId,
                        bookstoreId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró inventario con ID: "
                                        + inventoryId
                        )
                );
    }

    private Inventory getEntityByBookIdAndCondition(Long bookId, BookCondition condition) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return inventoryRepository
                .findWithBookDetailsByBookIdAndBookstoreIdAndCondition(
                        bookId,
                        bookstoreId,
                        condition
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró inventario para el libro con ID: " + bookId
                ));
    }

    private String normalizeSearchIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .trim()
                .toUpperCase()
                .replaceAll("[^0-9X]", "");

        return normalized.isBlank()
                ? null
                : normalized;
    }

    private InventorySummaryResponse toSummaryResponse(Inventory inventory) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(inventory.getBook().getId())
                .orElse(null);

        return inventoryMapper.toSummaryResponse(inventory, editorialPrice);
    }

    private InventoryDetailResponse toDetailResponse(Inventory inventory) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(inventory.getBook().getId())
                .orElse(null);

        List<BookProviderResponse> providers =
                providerBookService
                        .getProvidersForBook(
                                inventory.getBook().getId()
                        );

        return inventoryMapper.toDetailResponse(inventory, editorialPrice, providers);
    }

    private InventoryDetailResponse syncStockAndMap(Inventory inventory) {

        tiendanubeVariantSyncService.syncStock(
                inventory.getId(),
                inventory.getStock()
        );

        return toDetailResponse(inventory);
    }
}