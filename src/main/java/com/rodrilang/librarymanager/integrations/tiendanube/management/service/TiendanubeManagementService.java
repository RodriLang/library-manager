package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkOperationRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkSelectionRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeInventoryFilterRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeBulkOperationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeManagedInventoryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeManagementSummaryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.management.entity.TiendanubeBulkOperation;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkOperationStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationJdbcRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeManagementInventoryRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TiendanubeManagementService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeManagementInventoryRepository inventoryRepository;
    private final TiendanubeBulkOperationRepository operationRepository;
    private final TiendanubeBulkOperationJdbcRepository bulkJdbcRepository;
    private final TiendanubeWorkNotifier workNotifier;

    @Transactional(readOnly = true)
    public Page<TiendanubeManagedInventoryResponse> searchInventories(
            TiendanubeInventoryFilterRequest filter,
            Pageable pageable
    ) {
        return inventoryRepository.search(
                bookstoreContext.getCurrentBookstoreId(),
                filter != null ? filter : TiendanubeInventoryFilterRequest.empty(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public TiendanubeManagementSummaryResponse getSummary() {
        return inventoryRepository.summary(bookstoreContext.getCurrentBookstoreId());
    }

    @Transactional
    public TiendanubeBulkOperationResponse createBulkOperation(TiendanubeBulkOperationRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = requireStoreForAction(bookstoreId, request.action());
        Selection selection = resolveSelection(bookstoreId, request.selection());

        return createOperation(
                bookstoreId,
                store,
                request.action(),
                selection.type(),
                selection.description(),
                selection.inventoryIds()
        );
    }

    @Transactional(readOnly = true)
    public Page<TiendanubeBulkOperationResponse> getBulkOperations(Pageable pageable) {
        return bulkJdbcRepository.findOperations(bookstoreContext.getCurrentBookstoreId(), pageable);
    }

    @Transactional(readOnly = true)
    public TiendanubeBulkOperationResponse getBulkOperation(Long operationId) {
        return requireOperationResponse(operationId, bookstoreContext.getCurrentBookstoreId());
    }

    @Transactional(readOnly = true)
    public Page<TiendanubeBulkOperationItemResponse> getBulkOperationItems(Long operationId, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireOperation(operationId, bookstoreId);
        return bulkJdbcRepository.findItems(operationId, bookstoreId, pageable);
    }

    @Transactional
    public TiendanubeBulkOperationResponse cancelBulkOperation(Long operationId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeBulkOperation operation = requireOperation(operationId, bookstoreId);
        TiendanubeBulkOperationResponse current = requireOperationResponse(operationId, bookstoreId);

        if (current.status() != TiendanubeBulkOperationStatus.RUNNING
                && current.status() != TiendanubeBulkOperationStatus.CANCEL_REQUESTED) {
            throw new BusinessException("La operación masiva ya se encuentra finalizada");
        }

        Instant now = Instant.now();

        if (operation.getCancelRequestedAt() == null) {
            operation.setCancelRequestedAt(now);
            operationRepository.saveAndFlush(operation);
        }

        bulkJdbcRepository.cancelPendingItems(operationId, now);

        return requireOperationResponse(operationId, bookstoreId);
    }

    @Transactional
    public TiendanubeBulkOperationResponse retryFailed(Long operationId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeBulkOperation original = requireOperation(operationId, bookstoreId);
        List<Long> inventoryIds = bulkJdbcRepository.findFailedInventoryIds(operationId);

        if (inventoryIds.isEmpty()) {
            throw new BusinessException("La operación no tiene elementos fallidos para reintentar");
        }

        TiendanubeStore store = requireStoreForAction(bookstoreId, original.getAction());
        return createOperation(
                bookstoreId,
                store,
                original.getAction(),
                TiendanubeBulkSelectionType.IDS,
                "Reintento de fallidos de operación #" + operationId,
                inventoryIds
        );
    }

    private TiendanubeBulkOperationResponse createOperation(
            Long bookstoreId,
            TiendanubeStore store,
            TiendanubeBulkAction action,
            TiendanubeBulkSelectionType selectionType,
            String selectionDescription,
            List<Long> inventoryIds
    ) {
        TiendanubeBulkOperation operation = TiendanubeBulkOperation.builder()
                .bookstoreId(bookstoreId)
                .tiendanubeStoreId(store.getId())
                .storeId(store.getStoreId())
                .action(action)
                .selectionType(selectionType)
                .selectionDescription(selectionDescription)
                .build();

        TiendanubeBulkOperation saved = operationRepository.saveAndFlush(operation);
        bulkJdbcRepository.insertItems(saved.getId(), inventoryIds, Instant.now());
        if (!inventoryIds.isEmpty()) {
            workNotifier.notifyWork(TiendanubeWorkType.BULK);
        }
        return requireOperationResponse(saved.getId(), bookstoreId);
    }

    private Selection resolveSelection(Long bookstoreId, TiendanubeBulkSelectionRequest selection) {
        if (selection.type() == TiendanubeBulkSelectionType.IDS) {
            Set<Long> requestedIds = selection.inventoryIds() == null
                    ? Set.of()
                    : selection.inventoryIds().stream()
                    .filter(id -> id != null)
                    .collect(Collectors.toCollection(TreeSet::new));

            if (requestedIds.isEmpty()) {
                throw new BusinessException("Debe seleccionar al menos un inventario");
            }

            List<Long> existingIds = inventoryRepository.findExistingInventoryIds(bookstoreId, requestedIds);

            if (existingIds.size() != requestedIds.size()) {
                throw new BusinessException("Alguno de los inventarios seleccionados no existe o no pertenece a la librería");
            }

            return new Selection(
                    TiendanubeBulkSelectionType.IDS,
                    existingIds,
                    existingIds.size() + " inventarios seleccionados"
            );
        }

        TiendanubeInventoryFilterRequest filter = selection.filter() != null
                ? selection.filter()
                : TiendanubeInventoryFilterRequest.empty();

        List<Long> inventoryIds = inventoryRepository.findMatchingInventoryIds(bookstoreId, filter);
        return new Selection(TiendanubeBulkSelectionType.FILTER, inventoryIds, describeFilter(filter));
    }

    private String describeFilter(TiendanubeInventoryFilterRequest filter) {
        return "q=" + value(filter.q())
                + "; publication=" + filter.publication()
                + "; publisherId=" + value(filter.publisherId())
                + "; stock=" + filter.stock()
                + "; priceSyncEnabled=" + value(filter.priceSyncEnabled());
    }

    private String value(Object value) {
        return value != null ? value.toString() : "-";
    }

    private TiendanubeStore requireStoreForAction(Long bookstoreId, TiendanubeBulkAction action) {
        if (action.isLocalOnly()) {
            return storeRepository.findByBookstoreId(bookstoreId)
                    .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube registrada"));
        }

        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));

        if (!store.isTokenValid()) {
            throw new BusinessException("La conexión con Tiendanube necesita volver a autorizarse");
        }

        return store;
    }

    private TiendanubeBulkOperation requireOperation(Long operationId, Long bookstoreId) {
        return operationRepository.findByIdAndBookstoreId(operationId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la operación masiva " + operationId));
    }

    private TiendanubeBulkOperationResponse requireOperationResponse(Long operationId, Long bookstoreId) {
        return bulkJdbcRepository.findOperation(operationId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la operación masiva " + operationId));
    }

    private record Selection(
            TiendanubeBulkSelectionType type,
            List<Long> inventoryIds,
            String description
    ) {
    }
}
