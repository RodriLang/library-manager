package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.TiendanubeImportCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeImportPersistenceService;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
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
            TiendanubeImportCommand command,
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant
    ) {
        Book book = bookRepository.findById(command.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe el libro con id: " + command.bookId()));

        Bookstore bookstore = bookstoreRepository.findById(command.bookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe la librería con id: " + command.bookstoreId()));

        validateImportData(command);

        Inventory inventory = Inventory.builder()
                .book(book)
                .bookstore(bookstore)
                .condition(command.condition())
                .stock(command.stock())
                .minimumStock(0)
                .salePrice(command.salePrice())
                .tiendanubeStatus(TiendanubeInventoryStatus.LINKED)
                .tiendanubePriceSyncEnabled(Boolean.TRUE.equals(command.syncPrice()))
                .editorialPriceSyncEnabled(Boolean.TRUE.equals(command.editorialPriceSyncEnabled()))
                .active(true)
                .build();

        inventoryRepository.save(inventory);

        String sku = variant.sku();

        if (sku == null || sku.isBlank()) {
            sku = TiendanubeProductUtils.resolveRemoteIsbn(variant);
        }

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(command.storeId())
                .tiendanubeProductId(product.id())
                .tiendanubeVariantId(variant.id())
                .sku(sku)
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

    private void validateImportData(TiendanubeImportCommand command) {
        if (command.condition() == null) {
            throw new BusinessException("La condición es obligatoria");
        }

        if (command.stock() == null || command.stock() < 0) {
            throw new BusinessException("El stock no puede ser nulo ni negativo");
        }

        if (command.salePrice() == null || command.salePrice().signum() <= 0) {
            throw new BusinessException("El precio de venta debe ser mayor que cero");
        }
    }
}