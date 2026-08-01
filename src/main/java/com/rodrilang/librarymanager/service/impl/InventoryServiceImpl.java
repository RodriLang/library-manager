package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.ReactivateInventoryRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePublicationRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import com.rodrilang.librarymanager.mapper.InventoryMapper;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.model.Inventory;
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
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final BookService bookService;
    private final TiendanubeVariantSyncService tiendanubeVariantSyncService;
    private final EditorialPriceService editorialPriceService;
    private final BookstoreService bookstoreService;
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
                : TiendanubeInventoryStatus.DISABLED;

        boolean editorialPriceSyncEnabled =
                condition == BookCondition.NEW
                        && Boolean.TRUE.equals(request.editorialPriceSyncEnabled());

        Inventory inventory = Inventory.builder()
                .book(book)
                .bookstore(bookstore)
                .condition(condition)
                .stock(request.initialStock())
                .minimumStock(request.minimumStock() != null ? request.minimumStock() : 0)
                .salePrice(request.salePrice())
                .editorialPriceSyncEnabled(editorialPriceSyncEnabled)
                .tiendanubePriceSyncEnabled(Boolean.TRUE.equals(request.tiendanubePriceSyncEnabled()))
                .tiendanubeStatus(tiendanubeStatus)
                .active(true)
                .build();

        Inventory saved = inventoryRepository.save(inventory);

        if (saved.getTiendanubeStatus() == TiendanubeInventoryStatus.PENDING_PUBLICATION) {
            eventPublisher.publishEvent(new TiendanubePublicationRequestedEvent(saved.getId()));
        }

        return toDetailResponse(saved);
    }

    @Transactional
    @Override
    public InventoryDetailResponse addStock(Long bookId, InventoryQuantityRequest request) {

        Inventory inventory = getEntityByBookId(bookId);

        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException("El inventario se encuentra inactivo. Debe reactivarse antes de agregar stock.");
        }

        inventory.setStock(inventory.getStock() + request.quantity());

        return saveAndSyncStock(inventory);
    }

    @Transactional
    @Override
    public InventoryDetailResponse recordSale(Long bookId, InventoryQuantityRequest request) {
        Inventory inventory = getEntityByBookId(bookId);

        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException("El libro se encuentra inactivo en el inventario.");
        }

        if (inventory.getStock() < request.quantity()) {
            throw new BusinessException("No hay stock suficiente para registrar la venta.");
        }

        inventory.setStock(inventory.getStock() - request.quantity());

        return saveAndSyncStock(inventory);
    }

    @Override
    @Transactional
    public InventoryDetailResponse reactivate(Long bookId, ReactivateInventoryRequest request) {
        Inventory inventory = getEntityByBookId(bookId);

        if (Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException("El inventario ya se encuentra activo.");
        }

        inventory.setActive(true);
        inventory.setStock(request.stock());
        inventory.setSalePrice(request.salePrice());
        inventory.setMinimumStock(request.minimumStock() != null ? request.minimumStock() : 0);
        inventory.setCondition(request.condition() != null ? request.condition() : BookCondition.NEW);
        inventory.setEditorialPriceSyncEnabled(
                inventory.getCondition() == BookCondition.NEW
                        && Boolean.TRUE.equals(request.editorialPriceSyncEnabled())
        );
        inventory.setTiendanubePriceSyncEnabled(Boolean.TRUE.equals(request.tiendanubePriceSyncEnabled()));

        if (inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.DISABLED
                && Boolean.TRUE.equals(request.publishOnTiendanube())) {
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        }

        Inventory saved = inventoryRepository.save(inventory);

        if (saved.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED) {
            tiendanubeVariantSyncService.syncStock(saved.getId(), saved.getStock());

            if (Boolean.TRUE.equals(saved.getTiendanubePriceSyncEnabled())) {
                tiendanubeVariantSyncService.syncPrice(saved.getId());
            }
        }

        return toDetailResponse(saved);
    }

    @Transactional
    @Override
    public InventoryDetailResponse update(Long bookId, UpdateInventoryRequest request) {
        Inventory inventory = getEntityByBookId(bookId);
        BigDecimal previousSalePrice = inventory.getSalePrice();
        Boolean previousPriceSyncEnabled = inventory.getTiendanubePriceSyncEnabled();

        inventoryMapper.updateEntity(request, inventory);

        Inventory saved = inventoryRepository.save(inventory);

        boolean priceChanged = !Objects.equals(previousSalePrice, saved.getSalePrice());
        boolean priceSyncEnabled = Boolean.TRUE.equals(saved.getTiendanubePriceSyncEnabled());
        boolean priceSyncJustEnabled = !Boolean.TRUE.equals(previousPriceSyncEnabled) && priceSyncEnabled;

        if (priceSyncEnabled && (priceChanged || priceSyncJustEnabled)) {
            tiendanubeVariantSyncService.syncPrice(saved.getId());
        }

        return toDetailResponse(saved);
    }

    @Override
    public InventoryDetailResponse getByBookId(Long bookId) {

        log.info("Buscando libro con ID: {} en el inventario", bookId);
        Inventory inventory = getEntityByBookId(bookId);

        return toDetailResponse(inventory);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InventorySummaryResponse> getAll(Pageable pageable) {

        pageable = PageableUtils.mapSortProperties(pageable, INVENTORY_SORT_MAPPING);

        return inventoryRepository.findAllWithBookDetails(pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InventorySummaryResponse> search(String query, Pageable pageable) {

        if (query == null || query.isBlank()) {
            Pageable normalizedPageable = PageableUtils.mapSortProperties(pageable, INVENTORY_SORT_MAPPING);

            return inventoryRepository.findAllWithBookDetails(normalizedPageable)
                    .map(this::toSummaryResponse);
        }

        return inventoryRepository.search(query.trim(), pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional
    @Override
    public void removeBook(Long bookId) {

        Inventory inventory = getEntityByBookId(bookId);
        inventory.setActive(false);

        Inventory saved = inventoryRepository.save(inventory);

        if (saved.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED) {
            tiendanubeVariantSyncService.syncStock(saved.getId(), 0);
        }
    }

    @Override
    @Transactional
    public void decreaseStockFromTiendanube(Long inventoryId, Integer quantity) {
        validateQuantity(quantity);

        Inventory inventory = inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el inventario con id: " + inventoryId));

        if (inventory.getStock() < quantity) {
            throw new BusinessException("Stock insuficiente para el inventario: " + inventoryId);
        }

        inventory.setStock(inventory.getStock() - quantity);

        inventoryRepository.save(inventory);
    }

    @Override
    @Transactional
    public void increaseStockFromTiendanube(Long inventoryId, Integer quantity) {
        validateQuantity(quantity);

        Inventory inventory = inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el inventario con id: " + inventoryId));

        inventory.setStock(inventory.getStock() + quantity);

        inventoryRepository.save(inventory);
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("La cantidad debe ser mayor a cero");
        }
    }

    private Inventory getEntityByBookId(Long bookId) {

        return getEntityByBookIdAndCondition(bookId, BookCondition.NEW);
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

    private InventorySummaryResponse toSummaryResponse(Inventory inventory) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(inventory.getBook().getId())
                .orElse(null);

        return inventoryMapper.toSummaryResponse(inventory, editorialPrice);
    }

    private InventoryDetailResponse toDetailResponse(Inventory inventory) {
        EditorialPrice editorialPrice = editorialPriceService.findCurrentByBookId(inventory.getBook().getId())
                .orElse(null);

        return inventoryMapper.toDetailResponse(inventory, editorialPrice);
    }

    private InventoryDetailResponse saveAndSyncStock(Inventory inventory) {
        Inventory saved = inventoryRepository.save(inventory);
        tiendanubeVariantSyncService.syncStock(saved.getId(), saved.getStock());

        return toDetailResponse(saved);
    }
}