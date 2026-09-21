package com.rodrilang.librarymanager.inventory.bulk.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkFilterRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.repository.criteria.InventorySearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryBulkSelectionService {

    private final InventoryRepository inventoryRepository;
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

        List<Long> ids =
                requestedIds
                        .stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();

        List<Long> existingIds =
                inventoryRepository
                        .findIdsByBookstoreIdAndIdIn(
                                bookstoreId,
                                ids
                        );

        if (existingIds.size() != ids.size()) {
            throw new BusinessException(
                    "Uno o más inventarios no pertenecen a la librería actual"
            );
        }

        return existingIds;
    }

    private List<Long> resolveFilter(
            Long bookstoreId,
            InventoryBulkFilterRequest filter,
            Set<Long> excludedInventoryIds
    ) {
        InventoryBulkFilterRequest resolved =
                filter != null
                        ? filter
                        : new InventoryBulkFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        InventoryAdvancedFilters advancedFilters =
                new InventoryAdvancedFilters(
                        resolved.condition(),
                        resolved.publisherIds(),
                        resolved.authorIds(),
                        resolved.priceMode(),
                        resolved.active()
                );

        /*
         * force=true es intencional.
         *
         * Si el usuario llegó a seleccionar "todos los resultados",
         * la búsqueda ya fue ejecutada en InventoryPage.
         *
         * También permite reproducir búsquedas cortas que fueron
         * ejecutadas manualmente.
         */
        InventorySearchCriteria criteria =
                new InventorySearchCriteria(
                        resolved.q(),
                        true,
                        resolved.stock(),
                        advancedFilters
                );

        List<Long> excluded =
                excludedInventoryIds == null
                        ? List.of()
                        : excludedInventoryIds
                        .stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        return inventoryRepository.findIds(
                bookstoreId,
                criteria,
                excluded
        );
    }
}