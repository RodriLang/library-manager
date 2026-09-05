package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobEnqueueCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobEnqueueService;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationJdbcRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeBulkOperationJdbcRepository.DispatchItem;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeBulkOperationDispatcher {

    private final TiendanubeBulkOperationJdbcRepository bulkRepository;
    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeJobEnqueueService enqueueService;

    @Value("${tiendanube.bulk.dispatch-batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${tiendanube.bulk.dispatch-delay-ms:1000}",
            initialDelayString = "${tiendanube.bulk.initial-delay-ms:3000}"
    )
    @Transactional
    public void dispatchPendingItems() {
        List<DispatchItem> items = bulkRepository.claimPendingItems(batchSize);

        if (items.isEmpty()) {
            return;
        }

        Map<Long, Inventory> inventories = inventoryRepository.findAllById(
                        items.stream().map(DispatchItem::inventoryId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(Inventory::getId, Function.identity()));

        Map<Long, TiendanubeProductLink> links = productLinkRepository.findAllByInventoryIdInAndActiveTrue(
                        inventories.keySet()
                ).stream()
                .collect(Collectors.toMap(link -> link.getInventory().getId(), Function.identity()));

        Map<Long, TiendanubeStore> stores = storeRepository.findAllById(
                        items.stream().map(DispatchItem::tiendanubeStoreId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(TiendanubeStore::getId, Function.identity()));

        Instant now = Instant.now();

        for (DispatchItem item : items) {
            dispatch(
                    item,
                    inventories.get(item.inventoryId()),
                    links.get(item.inventoryId()),
                    stores.get(item.tiendanubeStoreId()),
                    now
            );
        }
    }

    private void dispatch(
            DispatchItem item,
            Inventory inventory,
            TiendanubeProductLink link,
            TiendanubeStore store,
            Instant now
    ) {
        if (inventory == null || !Boolean.TRUE.equals(inventory.getActive())) {
            skip(item, "INVENTORY_NOT_AVAILABLE", "El inventario ya no se encuentra activo", now);
            return;
        }

        if (!inventory.getBookstore().getId().equals(item.bookstoreId())) {
            skip(item, "BOOKSTORE_CHANGED", "El inventario ya no pertenece a la librería de la operación", now);
            return;
        }

        if (!isExpectedStoreIdentity(store, item)) {
            skip(item, "STORE_CHANGED", "La cuenta Tiendanube de la operación ya no coincide con la conexión actual", now);
            return;
        }

        switch (item.action()) {
            case UNLINK -> dispatchUnlink(item, inventory, link, store, now);
            case DISABLE_PRICE_SYNC -> dispatchDisablePriceSync(item, inventory, now);
            case ENABLE_PRICE_SYNC -> dispatchEnablePriceSync(item, inventory, link, store, now);
            case PUBLISH -> dispatchPublish(item, inventory, link, store, now);
            default -> dispatchLinkedJob(item, inventory, link, store, now);
        }
    }

    private void dispatchPublish(
            DispatchItem item,
            Inventory inventory,
            TiendanubeProductLink link,
            TiendanubeStore store,
            Instant now
    ) {
        if (!isUsableStore(store)) {
            return;
        }

        if (link != null) {
            skip(item, "ALREADY_PUBLISHED", "El inventario ya tiene una publicación vinculada", now);
            return;
        }

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        queue(item, inventory, store, TiendanubeJobType.PUBLISH, now);
    }

    private void dispatchLinkedJob(
            DispatchItem item,
            Inventory inventory,
            TiendanubeProductLink link,
            TiendanubeStore store,
            Instant now
    ) {
        if (!isUsableStore(store)) {
            return;
        }

        if (!validateActiveLink(item, inventory, link, store, now)) {
            return;
        }

        queue(item, inventory, store, item.action().jobType(), now);
    }

    private void dispatchEnablePriceSync(
            DispatchItem item,
            Inventory inventory,
            TiendanubeProductLink link,
            TiendanubeStore store,
            Instant now
    ) {
        if (!isUsableStore(store)) {
            return;
        }

        if (!validateActiveLink(item, inventory, link, store, now)) {
            return;
        }

        inventory.setTiendanubePriceSyncEnabled(true);
        queue(item, inventory, store, TiendanubeJobType.SYNC_PRICE, now);
    }

    private void dispatchDisablePriceSync(DispatchItem item, Inventory inventory, Instant now) {
        inventory.setTiendanubePriceSyncEnabled(false);
        bulkRepository.markCompleted(item.itemId(), now);
    }

    private void dispatchUnlink(
            DispatchItem item,
            Inventory inventory,
            TiendanubeProductLink link,
            TiendanubeStore store,
            Instant now
    ) {
        if (link == null) {
            skip(item, "NOT_PUBLISHED", "El inventario no tiene una publicación vinculada", now);
            return;
        }

        if (!store.getStoreId().equals(link.getTiendanubeStoreId())) {
            skip(item, "LINK_STORE_MISMATCH", "La publicación pertenece a otra cuenta Tiendanube", now);
            return;
        }

        link.setActive(false);
        link.setLastError(null);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        inventory.setTiendanubePriceSyncEnabled(false);
        bulkRepository.markCompleted(item.itemId(), now);
    }

    private boolean validateActiveLink(
            DispatchItem item,
            Inventory inventory,
            TiendanubeProductLink link,
            TiendanubeStore store,
            Instant now
    ) {
        if (link == null) {
            skip(item, "NOT_PUBLISHED", "El inventario no tiene una publicación vinculada", now);
            return false;
        }

        if (!store.getStoreId().equals(link.getTiendanubeStoreId())) {
            skip(item, "LINK_STORE_MISMATCH", "La publicación pertenece a otra cuenta Tiendanube", now);
            return false;
        }

        if (item.action() != TiendanubeBulkAction.DELETE_PUBLICATION
                && inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.LINKED
                && inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.SYNC_ERROR) {
            skip(item, "INVALID_STATUS",
                    "El inventario no puede sincronizarse en estado " + inventory.getTiendanubeStatus(), now);
            return false;
        }

        return true;
    }

    private void queue(
            DispatchItem item,
            Inventory inventory,
            TiendanubeStore store,
            TiendanubeJobType type,
            Instant now
    ) {
        Long jobId = bulkRepository.findCoalescableJobId(
                        inventory.getId(),
                        type.name(),
                        inventory.getBookstore().getId(),
                        store.getId(),
                        store.getStoreId()
                )
                .orElseGet(() -> enqueue(inventory, store, type));

        bulkRepository.markQueued(item.itemId(), jobId, now);
    }

    private Long enqueue(Inventory inventory, TiendanubeStore store, TiendanubeJobType type) {
        return enqueueService.enqueue(new TiendanubeJobEnqueueCommand(
                inventory.getBookstore().getId(),
                store.getId(),
                store.getStoreId(),
                inventory.getId(),
                type,
                TiendanubeJobSource.BULK,
                null
        ));
    }

    private boolean isExpectedStoreIdentity(TiendanubeStore store, DispatchItem item) {
        return store != null
                && store.getId().equals(item.tiendanubeStoreId())
                && store.getStoreId().equals(item.storeId())
                && store.getBookstore().getId().equals(item.bookstoreId());
    }

    private boolean isUsableStore(TiendanubeStore store) {
        return store.isActive() && store.isTokenValid();
    }

    private void skip(DispatchItem item, String reason, String message, Instant now) {
        bulkRepository.markSkipped(item.itemId(), reason, message, now);
        log.info("Tiendanube bulk item skipped. operationId={} inventoryId={} action={} reason={}",
                item.operationId(), item.inventoryId(), item.action(), reason);
    }
}
