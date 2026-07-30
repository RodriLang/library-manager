package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeImportPersistenceService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeImportPersistenceServiceImpl implements TiendanubeImportPersistenceService {

    private final BookRepository bookRepository;
    private final BookstoreRepository bookstoreRepository;
    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;

    @Override
    @Transactional
    public TiendanubeImportResultResponse importExistingBook(
            Long bookstoreId,
            Long bookId,
            Long storeId,
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant
    ) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el libro con id: " + bookId));

        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la librería con id: " + bookstoreId));

        Inventory inventory = Inventory.builder()
                .book(book)
                .bookstore(bookstore)
                .condition(BookCondition.NEW)
                .stock(variant.stock() != null ? variant.stock() : 0)
                .minimumStock(0)
                .salePrice(variant.price())
                .tiendanubeStatus(TiendanubeInventoryStatus.LINKED)
                .active(true)
                .build();

        inventoryRepository.save(inventory);

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(storeId)
                .tiendanubeProductId(product.id())
                .tiendanubeVariantId(variant.id())
                .sku(variant.sku())
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);

        return new TiendanubeImportResultResponse(
                inventory.getId(),
                book.getId(),
                product.id(),
                variant.id(),
                TiendanubeInventoryStatus.LINKED,
                false
        );
    }
}