package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeStockSyncService;
import com.rodrilang.librarymanager.mapper.InventoryMapper;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final BookService bookService;
    private final TiendanubeStockSyncService tiendanubeStockSyncService;

    @Transactional
    @Override
    public InventoryDetailResponse addToInventory(Long bookId, AddBookToInventoryRequest request) {

        Book book = bookService.getEntityById(bookId);

        if (inventoryRepository.existsByBookId(bookId)) {
            throw new DuplicateResourceException(String.format(
                    "El libro ISBN: %s ya se encuentra registrado en el inventario", book.getIsbn())
            );
        }

        Inventory inventory = Inventory.builder()
                .book(book)
                .stock(request.initialStock())
                .minimumStock(request.minimumStock() != null ? request.minimumStock() : 0)
                .active(true)
                .build();

        return saveAndSyncStock(inventory, bookId);
    }

    @Transactional
    @Override
    public InventoryDetailResponse addStock(Long bookId, InventoryQuantityRequest request) {

        Inventory inventory = getEntityByBookId(bookId);
        inventory.setStock(inventory.getStock() + request.quantity());

        return saveAndSyncStock(inventory, bookId);
    }

    @Transactional
    @Override
    public InventoryDetailResponse recordSale(Long bookId, InventoryQuantityRequest request) {

        Inventory inventory = getEntityByBookId(bookId);

        if (inventory.getStock() < request.quantity()) {
            throw new BusinessException(
                    "No hay stock suficiente para registrar la venta."
            );
        }

        inventory.setStock(inventory.getStock() - request.quantity());

        return saveAndSyncStock(inventory, bookId);
    }

    @Transactional
    @Override
    public InventoryDetailResponse update(Long bookId, UpdateInventoryRequest request) {

        Inventory inventory = getEntityByBookId(bookId);

        inventoryMapper.updateEntity(request, inventory);

        return saveAndMapToDetailResponse(inventory);
    }

    @Override
    public InventoryDetailResponse getByBookId(Long bookId) {

        log.info("Buscando libro con ID: {} en el inventario", bookId);
        Inventory inventory = getEntityByBookId(bookId);

        return inventoryMapper.toDetailResponse(inventory);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InventorySummaryResponse> getAll(Pageable pageable) {

        return inventoryRepository.findAllWithBookDetails(pageable)
                .map(inventoryMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<InventorySummaryResponse> search(String query, Pageable pageable) {

        if (query == null || query.isBlank()) {
            return inventoryRepository.findAllWithBookDetails(pageable)
                    .map(inventoryMapper::toSummaryResponse);
        }

        return inventoryRepository.search(query.trim(), pageable)
                .map(inventoryMapper::toSummaryResponse);
    }

    @Transactional
    @Override
    public void removeBook(Long bookId) {

        Inventory inventory = getEntityByBookId(bookId);
        inventory.setActive(false);

        inventoryRepository.save(inventory);
    }

    @Transactional
    @Override
    public void decreaseStockByBookId(Long bookId, Integer quantity) {

        validateQuantity(quantity);

        Inventory inventory = getEntityByBookId(bookId);

        int newStock = inventory.getStock() - quantity;

        if (newStock < 0) {
            throw new BusinessException("El stock no puede ser negativo");
        }

        inventory.setStock(newStock);

        inventoryRepository.save(inventory);
    }

    @Transactional
    @Override
    public void increaseStockByBookId(Long bookId, Integer quantity) {

        validateQuantity(quantity);

        Inventory inventory = getEntityByBookId(bookId);
        int newStock = inventory.getStock() + quantity;

        inventory.setStock(newStock);

        inventoryRepository.save(inventory);
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("La cantidad debe ser mayor a cero");
        }
    }

    private Inventory getEntityByBookId(Long bookId) {

        return inventoryRepository.findWithBookDetailsByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró inventario para el libro con ID: " + bookId
                ));
    }

    private InventoryDetailResponse saveAndMapToDetailResponse(Inventory inventory) {
        Inventory saved = inventoryRepository.save(inventory);
        return inventoryMapper.toDetailResponse(saved);
    }

    private InventoryDetailResponse saveAndSyncStock(Inventory inventory, Long bookId) {
        Inventory saved = inventoryRepository.save(inventory);

        tiendanubeStockSyncService.syncStockByBookId(
                bookId,
                inventory.getStock()
        );

        return inventoryMapper.toDetailResponse(saved);
    }
}