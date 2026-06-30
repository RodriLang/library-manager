package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.AddStockRequest;
import com.rodrilang.librarymanager.dto.request.RegisterManualBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.RegisterPurchaseItemRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryStatusRequest;
import com.rodrilang.librarymanager.dto.request.UpdatePriceRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.InventoryMapper;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookCatalogService;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.InventoryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final BookService bookService;
    private final BookCatalogService bookCatalogService;

    @Transactional
    @Override
    public InventoryDetailResponse addStock(AddStockRequest request) {

        Book book = bookService.getEntityById(request.bookId());

        Inventory inventory = inventoryRepository
                .findByBookId(book.getId())
                .orElseGet(() -> Inventory.builder()
                        .book(book)
                        .stock(0)
                        .costPrice(request.costPrice())
                        .salePrice(request.salePrice())
                        .active(true)
                        .build());

        inventory.setStock(inventory.getStock() + request.quantity());
        inventory.setCostPrice(request.costPrice());
        inventory.setSalePrice(request.salePrice());

        Inventory saved = inventoryRepository.save(inventory);

        return inventoryMapper.toDetailResponse(saved);
    }

    @Transactional
    @Override
    public InventoryDetailResponse updatePrice(Long inventoryId, UpdatePriceRequest request) {

        Inventory inventory = getEntityById(inventoryId);

        inventory.setSalePrice(request.salePrice());

        return inventoryMapper.toDetailResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    @Override
    public InventoryDetailResponse updateActive(Long inventoryId, UpdateInventoryStatusRequest request) {

        Inventory inventory = getEntityById(inventoryId);

        inventory.setActive(request.active());

        return inventoryMapper.toDetailResponse(inventoryRepository.save(inventory));
    }

    @Override
    public InventoryDetailResponse getByBookId(Long bookId) {

        log.info("Buscando libro con ID: {} en el inventario", bookId);
        Inventory inventory = inventoryRepository.findByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró inventario para el libro con ID: " + bookId
                ));

        return inventoryMapper.toDetailResponse(inventory);
    }

    @Override
    public InventoryDetailResponse getByIsbn(String isbn) {

        Inventory inventory = inventoryRepository.findByBookIsbn(isbn)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró inventario para el ISBN: " + isbn
                ));

        return inventoryMapper.toDetailResponse(inventory);
    }

    @Transactional
    @Override
    public List<InventoryDetailResponse> registerPurchase(List<RegisterPurchaseItemRequest> request) {
        return request.stream()
                .map(this::registerPurchaseItem)
                .toList();
    }

    @Transactional
    @Override
    public InventoryDetailResponse registerPurchaseWithManualBook(RegisterManualBookPurchaseRequest request) {

        BookDetailResponse bookResponse = bookService.create(request.book());

        Book book = bookService.getEntityById(bookResponse.id());

        Inventory inventory = inventoryRepository.findByBookId(book.getId())
                .orElseGet(() -> Inventory.builder()
                        .book(book)
                        .stock(0)
                        .costPrice(request.costPrice())
                        .salePrice(request.salePrice())
                        .active(true)
                        .build());

        inventory.setStock(inventory.getStock() + request.quantity());
        inventory.setCostPrice(request.costPrice());
        inventory.setSalePrice(request.salePrice());

        Inventory saved = inventoryRepository.save(inventory);

        return inventoryMapper.toDetailResponse(saved);
    }

    private InventoryDetailResponse registerPurchaseItem(RegisterPurchaseItemRequest request) {

        Book book = bookCatalogService.getOrCreateByIsbn(request.isbn());

        Inventory inventory = inventoryRepository.findByBookId(book.getId())
                .orElseGet(() -> Inventory.builder()
                        .book(book)
                        .stock(0)
                        .costPrice(request.costPrice())
                        .salePrice(request.salePrice())
                        .active(true)
                        .build());

        inventory.setStock(inventory.getStock() + request.quantity());
        inventory.setCostPrice(request.costPrice());
        inventory.setSalePrice(request.salePrice());

        Inventory saved = inventoryRepository.save(inventory);

        return inventoryMapper.toDetailResponse(saved);
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

    private Inventory getEntityById(Long id) {

        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró inventario con ID: " + id
                ));
    }
}