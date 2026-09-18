package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisCreateInventoryRequest;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockAdjustmentCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisInventoryService {

    private final BookRepository bookRepository;
    private final InventoryRepository inventoryRepository;
    private final BookstoreService bookstoreService;
    private final InventoryStockService inventoryStockService;

    public Inventory createOrReactivate(
            Long bookstoreId,
            TiendanubeImportAnalysisCreateInventoryRequest request
    ) {
        Book book = bookRepository.findByIdWithDetails(request.bookId())
                .orElseThrow(() -> new BusinessException(
                        "No existe el libro de catálogo con id " + request.bookId()
                ));

        if (!Boolean.TRUE.equals(book.getActive())) {
            throw new BusinessException("El libro seleccionado está inactivo en el catálogo");
        }

        return inventoryRepository
                .findByBookIdAndBookstoreIdAndCondition(
                        book.getId(),
                        bookstoreId,
                        BookCondition.NEW
                )
                .map(existing -> reactivate(existing, bookstoreId, request))
                .orElseGet(() -> create(book, bookstoreId, request));
    }

    private Inventory create(
            Book book,
            Long bookstoreId,
            TiendanubeImportAnalysisCreateInventoryRequest request
    ) {
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        Inventory inventory = Inventory.builder()
                .book(book)
                .bookstore(bookstore)
                .condition(BookCondition.NEW)
                .salePrice(request.salePrice())
                .stock(0)
                .minimumStock(request.minimumStock())
                .tiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED)
                .tiendanubePriceSyncEnabled(false)
                .editorialPriceSyncEnabled(false)
                .active(true)
                .build();

        Inventory saved = inventoryRepository.save(inventory);

        if (request.initialStock() == 0) {
            return saved;
        }

        return inventoryStockService.changeStock(
                saved.getId(),
                new InventoryStockChangeCommand(
                        request.initialStock(),
                        InventoryMovementType.INITIAL_STOCK,
                        InventoryMovementSource.MANUAL,
                        null,
                        null,
                        "Stock inicial informado al vincular una publicación existente de Tiendanube"
                )
        ).inventory();
    }

    private Inventory reactivate(
            Inventory existing,
            Long bookstoreId,
            TiendanubeImportAnalysisCreateInventoryRequest request
    ) {
        Inventory inventory = inventoryRepository.findByIdForUpdate(existing.getId())
                .orElseThrow(() -> new BusinessException(
                        "No se pudo bloquear el inventario existente con id " + existing.getId()
                ));

        if (!bookstoreId.equals(inventory.getBookstore().getId())) {
            throw new BusinessException("El inventario existente pertenece a otra librería");
        }

        if (Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException(
                    "El libro ya está activo en el inventario NEW. Actualizá el análisis y vinculá ese inventario."
            );
        }

        inventory.setActive(true);
        inventory.setCondition(BookCondition.NEW);
        inventory.setSalePrice(request.salePrice());
        inventory.setMinimumStock(request.minimumStock());
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        inventory.setTiendanubePriceSyncEnabled(false);
        inventory.setEditorialPriceSyncEnabled(false);

        return inventoryStockService.adjustStockTo(
                inventory.getId(),
                new InventoryStockAdjustmentCommand(
                        request.initialStock(),
                        InventoryMovementSource.MANUAL,
                        null,
                        null,
                        "Stock informado al reactivar inventario desde la reconciliación de Tiendanube"
                )
        ).inventory();
    }
}
