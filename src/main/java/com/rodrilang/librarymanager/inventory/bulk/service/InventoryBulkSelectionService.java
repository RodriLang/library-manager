package com.rodrilang.librarymanager.inventory.bulk.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkFilterRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.repository.InventoryBulkSelectionRepository;
import com.rodrilang.librarymanager.inventory.bulk.specification.InventoryBulkSpecificationFactory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryBulkSelectionService {

    private final InventoryRepository inventoryRepository;
    private final InventoryBulkSelectionRepository selectionRepository;
    private final InventoryBulkSpecificationFactory specificationFactory;
    private final BookstoreContext bookstoreContext;

    public List<Long> resolveIds(
            InventoryBulkSelectionRequest selection
    ) {
        if (selection == null || selection.type() == null) {
            throw new BusinessException("La selección es obligatoria");
        }

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        return switch (selection.type()) {
            case IDS -> resolveExplicitIds(
                    bookstoreId,
                    selection.inventoryIds()
            );

            case FILTER -> resolveFilter(
                    bookstoreId,
                    selection.filter(),
                    selection.excludedInventoryIds()
            );
        };
    }

    private List<Long> resolveExplicitIds(Long bookstoreId, Set<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new BusinessException("Debe seleccionar al menos un inventario");
        }

        List<Long> ids = requestedIds
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<Long> existingIds =
                inventoryRepository.findIdsByBookstoreIdAndIdIn(
                        bookstoreId,
                        ids
                );

        if (existingIds.size() != ids.size()) {
            throw new BusinessException("Uno o más inventarios no pertenecen a la librería actual");
        }

        return existingIds;
    }

    private List<Long> resolveFilter(
            Long bookstoreId,
            InventoryBulkFilterRequest filter,
            Set<Long> excludedIds
    ) {
        return selectionRepository.findIds(
                specificationFactory.build(bookstoreId, filter),
                excludedIds
        );
    }
}