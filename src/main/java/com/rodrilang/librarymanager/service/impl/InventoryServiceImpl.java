package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.InventoryMapper;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.InventoryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final BookService bookService;

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

        return saveAndMapToDetailResponse(inventory);
    }

    @Transactional
    @Override
    public InventoryDetailResponse addStock(Long bookId, InventoryQuantityRequest request) {

        Inventory inventory = getEntityByBookId(bookId);
        inventory.setStock(inventory.getStock() + request.quantity());

        return saveAndMapToDetailResponse(inventory);
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

        return saveAndMapToDetailResponse(inventory);
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

    @Override
    public Page<InventorySummaryResponse> getAll(Pageable pageable) {

        return inventoryRepository.findAll(pageable)
                .map(inventoryMapper::toSummaryResponse);
    }

    @Override
    public Page<InventorySummaryResponse> search(String query, Pageable pageable) {

        if (query == null || query.isBlank()) {
            return inventoryRepository.findAll(pageable)
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

    private Inventory getEntityByBookId(Long bookId) {

        return inventoryRepository.findByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró inventario para el libro con ID: " + bookId
                ));
    }

    private InventoryDetailResponse saveAndMapToDetailResponse(Inventory inventory) {
        Inventory saved = inventoryRepository.save(inventory);
        return inventoryMapper.toDetailResponse(saved);
    }
}