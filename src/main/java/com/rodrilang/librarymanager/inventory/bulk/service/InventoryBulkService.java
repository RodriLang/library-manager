package com.rodrilang.librarymanager.inventory.bulk.service;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkActionResponse;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkOperation;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkSelectionType;
import com.rodrilang.librarymanager.inventory.bulk.repository.InventoryBulkOperationRepository;
import com.rodrilang.librarymanager.inventory.bulk.service.action.InventoryBulkActionHandler;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryBulkService {

    private static final int BATCH_SIZE = 500;

    private final InventoryRepository inventoryRepository;
    private final InventoryBulkSelectionService selectionService;
    private final InventoryBulkOperationRepository operationRepository;

    private final List<InventoryBulkActionHandler> handlers;

    private final BookstoreContext bookstoreContext;
    private final BookstoreService bookstoreService;
    private final UserRepository userRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InventoryBulkActionResponse execute(
            InventoryBulkActionRequest request
    ) {
        validateRequest(request);

        List<Long> inventoryIds =
                selectionService.resolveIds(
                        request.selection()
                );

        if (inventoryIds.isEmpty()) {
            throw new BusinessException(
                    "No hay inventarios que coincidan con la selección"
            );
        }

        InventoryBulkActionHandler handler =
                findHandler(request.action());

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        int affected = 0;
        int skipped = 0;

        for (
                int from = 0;
                from < inventoryIds.size();
                from += BATCH_SIZE
        ) {
            int to = Math.min(
                    from + BATCH_SIZE,
                    inventoryIds.size()
            );

            List<Long> batchIds =
                    inventoryIds.subList(from, to);

            List<Inventory> inventories =
                    inventoryRepository
                            .findAllByBookstoreIdAndIdsForUpdate(
                                    bookstoreId,
                                    batchIds
                            );

            if (inventories.size() != batchIds.size()) {
                throw new BusinessException(
                        "Uno o más inventarios dejaron de estar disponibles durante la operación"
                );
            }

            List<Inventory> changed =
                    new ArrayList<>();

            for (Inventory inventory : inventories) {
                InventoryBulkMutationResult result =
                        handler.apply(
                                inventory,
                                request
                        );

                if (!result.changed()) {
                    skipped++;
                    continue;
                }

                affected++;
                changed.add(inventory);

                publishSyncEvents(
                        inventory,
                        result
                );
            }

            if (!changed.isEmpty()) {
                inventoryRepository.saveAll(changed);
            }
        }

        InventoryBulkOperation operation =
                saveOperation(
                        request,
                        inventoryIds.size(),
                        affected,
                        skipped
                );

        return new InventoryBulkActionResponse(
                operation.getId(),
                request.action(),
                inventoryIds.size(),
                affected,
                skipped
        );
    }

    private void validateRequest(
            InventoryBulkActionRequest request
    ) {
        if (request.action()
                == InventoryBulkAction.SET_MINIMUM_STOCK
                && request.minimumStock() == null) {
            throw new BusinessException(
                    "Debe indicar el stock mínimo"
            );
        }

        if (request.action()
                != InventoryBulkAction.SET_MINIMUM_STOCK
                && request.minimumStock() != null) {
            throw new BusinessException(
                    "La acción seleccionada no utiliza stock mínimo"
            );
        }
    }

    private InventoryBulkActionHandler findHandler(
            InventoryBulkAction action
    ) {
        return handlers
                .stream()
                .filter(handler ->
                        handler.supports(action)
                )
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(
                                "La acción masiva no está implementada: "
                                        + action
                        )
                );
    }

    private void publishSyncEvents(
            Inventory inventory,
            InventoryBulkMutationResult result
    ) {
        if (result.syncStock()) {
            eventPublisher.publishEvent(
                    new TiendanubeSyncRequestedEvent(
                            inventory.getId(),
                            TiendanubeSyncType.STOCK
                    )
            );
        }

        if (result.syncPrice()) {
            eventPublisher.publishEvent(
                    new TiendanubeSyncRequestedEvent(
                            inventory.getId(),
                            TiendanubeSyncType.PRICE
                    )
            );
        }
    }

    private InventoryBulkOperation saveOperation(
            InventoryBulkActionRequest request,
            int selected,
            int affected,
            int skipped
    ) {
        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        Long userId =
                bookstoreContext.getCurrentUserId();

        Bookstore bookstore =
                bookstoreService.getEntityById(
                        bookstoreId
                );

        User user =
                userRepository
                        .findByIdAndEnabledTrueAndAccountLockedFalse(
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "No se encontró el usuario autenticado"
                                )
                        );

        InventoryBulkOperation operation =
                InventoryBulkOperation.builder()
                        .bookstore(bookstore)
                        .createdByUser(user)
                        .action(request.action())
                        .selectionType(
                                request.selection().type()
                        )
                        .selectedCount(selected)
                        .affectedCount(affected)
                        .skippedCount(skipped)
                        .parameterValue(
                                parameterValue(request)
                        )
                        .selectionSummary(
                                selectionSummary(request)
                        )
                        .build();

        return operationRepository.save(operation);
    }

    private String parameterValue(
            InventoryBulkActionRequest request
    ) {
        if (request.action()
                == InventoryBulkAction.SET_MINIMUM_STOCK) {
            return "minimumStock="
                    + request.minimumStock();
        }

        return null;
    }

    private String selectionSummary(
            InventoryBulkActionRequest request
    ) {
        if (request.selection().type()
                == InventoryBulkSelectionType.IDS) {
            int count =
                    request.selection().inventoryIds() != null
                            ? request.selection().inventoryIds().size()
                            : 0;

            return "IDS count=" + count;
        }

        var filter = request.selection().filter();

        int excluded =
                request.selection().excludedInventoryIds() != null
                        ? request.selection()
                        .excludedInventoryIds()
                        .size()
                        : 0;

        if (filter == null) {
            return "FILTER all; excluded=" + excluded;
        }

        return "FILTER q=" + filter.q()
                + "; condition=" + filter.condition()
                + "; active=" + filter.active()
                + "; publisherId=" + filter.publisherId()
                + "; stock=" + filter.stock()
                + "; excluded=" + excluded;
    }
}