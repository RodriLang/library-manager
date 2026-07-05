package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final BookService bookService;
    private final EditorialPriceService editorialPriceService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    private static final Map<String, String> INVENTORY_SORT_MAPPING = Map.of("title", "book.titleSort");

    @Transactional
    @Override
    public InventoryDetailResponse addToInventory(Long bookId, AddBookToInventoryRequest request) {

        Book book = bookService.getEntityById(bookId);

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        BookCondition condition = request.condition() != null
                ? request.condition()
                : BookCondition.NEW;

        if (inventoryRepository.existsByBookIdAndBookstoreIdAndCondition(
                bookId,
                bookstoreId,
                condition
        )) {
            throw new DuplicateResourceException(String.format(
                    "El libro ISBN: %s ya se encuentra registrado en el inventario como %s",
                    book.getIsbn(),
                    condition
            ));
        }

        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        Inventory inventory = Inventory.builder()
                .book(book)
                .bookstore(bookstore)
                .condition(condition)
                .stock(request.initialStock())
                .minimumStock(request.minimumStock() != null ? request.minimumStock() : 0)
                .salePrice(request.salePrice())
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

        inventoryRepository.save(inventory);
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

    private InventoryDetailResponse saveAndMapToDetailResponse(Inventory inventory) {
        Inventory saved = inventoryRepository.save(inventory);
        return toDetailResponse(saved);
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
}