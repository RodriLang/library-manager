package com.rodrilang.librarymanager.inventory.bulk.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkFilterRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkItemResponse;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkPreviewResponse;
import com.rodrilang.librarymanager.inventory.bulk.specification.InventoryBulkSpecificationFactory;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryBulkQueryService {

    private static final int BATCH_SIZE = 500;

    private static final Map<String, String> SORT_MAPPING = Map.of(
            "title", "book.titleSort",
            "publisher", "book.publisher.name",
            "stock", "stock",
            "minimumStock", "minimumStock",
            "salePrice", "salePrice",
            "condition", "condition",
            "active", "active"
    );

    private final InventoryRepository inventoryRepository;
    private final InventoryBulkSpecificationFactory specificationFactory;
    private final InventoryBulkSelectionService selectionService;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public Page<InventoryBulkItemResponse> find(
            InventoryBulkFilterRequest filter,
            Pageable pageable
    ) {
        Pageable normalizedPageable =
                PageableUtils.mapSortProperties(
                        pageable,
                        SORT_MAPPING
                );

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        return inventoryRepository
                .findAll(
                        specificationFactory.build(
                                bookstoreId,
                                filter
                        ),
                        normalizedPageable
                )
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public InventoryBulkPreviewResponse preview(
            InventoryBulkSelectionRequest selection
    ) {
        List<Long> ids =
                selectionService.resolveIds(selection);

        if (ids.isEmpty()) {
            return new InventoryBulkPreviewResponse(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        long active = 0;
        long inactive = 0;
        long withStock = 0;
        long withoutStock = 0;
        long newItems = 0;
        long usedItems = 0;
        long linked = 0;

        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            int to = Math.min(
                    from + BATCH_SIZE,
                    ids.size()
            );

            List<Long> batchIds =
                    ids.subList(from, to);

            List<Inventory> inventories =
                    inventoryRepository
                            .findAllByBookstoreIdAndIdIn(
                                    bookstoreId,
                                    batchIds
                            );

            for (Inventory inventory : inventories) {
                if (Boolean.TRUE.equals(inventory.getActive())) {
                    active++;
                } else {
                    inactive++;
                }

                if (inventory.getStock() > 0) {
                    withStock++;
                } else {
                    withoutStock++;
                }

                if (inventory.getCondition() == BookCondition.NEW) {
                    newItems++;
                } else {
                    usedItems++;
                }

                if (inventory.getTiendanubeStatus()
                        == TiendanubeInventoryStatus.LINKED) {
                    linked++;
                }
            }
        }

        return new InventoryBulkPreviewResponse(
                ids.size(),
                active,
                inactive,
                withStock,
                withoutStock,
                newItems,
                usedItems,
                linked
        );
    }

    private InventoryBulkItemResponse toResponse(
            Inventory inventory
    ) {
        Book book = inventory.getBook();

        return new InventoryBulkItemResponse(
                inventory.getId(),
                book.getId(),
                book.getTitle(),
                book.getPreferredIsbn(),
                book.getPublisher() != null
                        ? book.getPublisher().getName()
                        : null,
                book.getCoverUrl(),
                inventory.getCondition(),
                inventory.getStock(),
                inventory.getMinimumStock(),
                inventory.getSalePrice(),
                Boolean.TRUE.equals(inventory.getActive()),
                Boolean.TRUE.equals(
                        inventory.getEditorialPriceSyncEnabled()
                ),
                Boolean.TRUE.equals(
                        inventory.getTiendanubePriceSyncEnabled()
                ),
                inventory.getTiendanubeStatus()
        );
    }
}