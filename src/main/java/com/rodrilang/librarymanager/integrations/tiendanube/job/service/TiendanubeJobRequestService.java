package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRetryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobEnqueueCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeJobRequestService {

    private static final int UNSAFE_OPERATION_MAX_ATTEMPTS = 1;

    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeJobEnqueueService enqueueService;

    @Transactional
    public Optional<Long> enqueueAutomaticLinked(Long inventoryId, TiendanubeJobType type) {
        validateLinkedType(type);

        return productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .filter(link -> canQueueLinkedOperation(link, type))
                .flatMap(link -> enqueueLinkedIfConnected(link, type, TiendanubeJobSource.AUTOMATIC));
    }

    @Transactional
    public List<Long> enqueueAutomaticLinked(Collection<Long> inventoryIds, TiendanubeJobType type) {
        validateLinkedType(type);

        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return List.of();
        }

        List<TiendanubeProductLink> links = productLinkRepository.findAllByInventoryIdInAndActiveTrue(inventoryIds);
        Map<Long, TiendanubeStore> stores = activeStoresByRemoteId(links);

        return links.stream()
                .filter(link -> canQueueLinkedOperation(link, type))
                .filter(link -> isUsable(stores.get(link.getTiendanubeStoreId())))
                .map(link -> enqueueLinked(link, stores.get(link.getTiendanubeStoreId()), type, TiendanubeJobSource.AUTOMATIC))
                .toList();
    }

    @Transactional
    public Optional<Long> enqueueAutomaticPublish(Long inventoryId) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElse(null);

        if (inventory == null) {
            log.warn("No se encoló publicación Tiendanube porque el inventario no existe. inventoryId={}", inventoryId);
            return Optional.empty();
        }

        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(inventory.getBookstore().getId()).orElse(null);

        if (!isUsable(store)) {
            log.info("No se encoló publicación Tiendanube porque la conexión no está disponible. inventoryId={}", inventoryId);
            return Optional.empty();
        }

        if (productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).isPresent()) {
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
            return Optional.empty();
        }

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        return Optional.of(enqueue(inventory, store, TiendanubeJobType.PUBLISH, TiendanubeJobSource.AUTOMATIC));
    }

    @Transactional
    public Long enqueueManualPublish(Long inventoryId) {
        Inventory inventory = requireInventory(inventoryId);
        TiendanubeStore store = requireUsableStore(inventory);

        if (productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).isPresent()) {
            throw new BusinessException("El inventario ya tiene una publicación vinculada en Tiendanube");
        }

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        return enqueue(inventory, store, TiendanubeJobType.PUBLISH, TiendanubeJobSource.MANUAL);
    }

    @Transactional
    public Long enqueueManualLinked(Long inventoryId, TiendanubeJobType type) {
        validateLinkedType(type);
        TiendanubeProductLink link = requireActiveLink(inventoryId);

        if (!canQueueLinkedOperation(link, type)) {
            throw new BusinessException("El inventario no puede sincronizarse en estado "
                    + link.getInventory().getTiendanubeStatus());
        }

        TiendanubeStore store = requireUsableStore(link);
        return enqueueLinked(link, store, type, TiendanubeJobSource.MANUAL);
    }

    @Transactional
    public Long enqueueManualDelete(Long inventoryId) {
        TiendanubeProductLink link = requireActiveLink(inventoryId);
        TiendanubeStore store = requireUsableStore(link);
        return enqueueLinked(link, store, TiendanubeJobType.DELETE_PUBLICATION, TiendanubeJobSource.MANUAL);
    }

    @Transactional
    public TiendanubeRetryResponse enqueueManualRetry(Long inventoryId) {
        Inventory inventory = requireInventory(inventoryId);

        if (inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.SYNC_ERROR) {
            throw new BusinessException("El inventario no se encuentra en estado de error");
        }

        Optional<TiendanubeProductLink> link = productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId);

        if (link.isPresent()) {
            TiendanubeStore store = requireUsableStore(link.get());
            enqueueLinked(link.get(), store, TiendanubeJobType.SYNC_PUBLICATION, TiendanubeJobSource.MANUAL);
            return new TiendanubeRetryResponse(inventoryId, inventory.getTiendanubeStatus(), "SYNC");
        }

        TiendanubeStore store = requireUsableStore(inventory);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
        enqueue(inventory, store, TiendanubeJobType.PUBLISH, TiendanubeJobSource.MANUAL);
        return new TiendanubeRetryResponse(inventoryId, TiendanubeInventoryStatus.PENDING_PUBLICATION, "PUBLISH");
    }

    private Optional<Long> enqueueLinkedIfConnected(TiendanubeProductLink link, TiendanubeJobType type,
                                                     TiendanubeJobSource source) {
        TiendanubeStore store = storeRepository.findByStoreIdAndActiveTrue(link.getTiendanubeStoreId()).orElse(null);

        if (!isUsable(store)) {
            log.info("No se encoló job Tiendanube porque la conexión no está disponible. inventoryId={} type={}",
                    link.getInventory().getId(), type);
            return Optional.empty();
        }

        return Optional.of(enqueueLinked(link, store, type, source));
    }

    private Long enqueueLinked(TiendanubeProductLink link, TiendanubeStore store, TiendanubeJobType type,
                               TiendanubeJobSource source) {
        return enqueue(link.getInventory(), store, type, source);
    }

    private Long enqueue(Inventory inventory, TiendanubeStore store, TiendanubeJobType type, TiendanubeJobSource source) {
        Integer maxAttempts = isUnsafeUntilStage3(type) ? UNSAFE_OPERATION_MAX_ATTEMPTS : null;

        return enqueueService.enqueue(new TiendanubeJobEnqueueCommand(
                inventory.getBookstore().getId(),
                store.getId(),
                store.getStoreId(),
                inventory.getId(),
                type,
                source,
                maxAttempts
        ));
    }

    private Map<Long, TiendanubeStore> activeStoresByRemoteId(List<TiendanubeProductLink> links) {
        List<Long> storeIds = links.stream().map(TiendanubeProductLink::getTiendanubeStoreId).distinct().toList();

        if (storeIds.isEmpty()) {
            return Map.of();
        }

        return storeRepository.findAllByStoreIdInAndActiveTrue(storeIds).stream()
                .collect(Collectors.toMap(TiendanubeStore::getStoreId, Function.identity()));
    }

    private Inventory requireInventory(Long inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));
    }

    private TiendanubeProductLink requireActiveLink(Long inventoryId) {
        return productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .orElseThrow(() -> new BusinessException("El inventario no tiene un vínculo activo con Tiendanube"));
    }

    private TiendanubeStore requireUsableStore(Inventory inventory) {
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(inventory.getBookstore().getId())
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));

        if (!store.isTokenValid()) {
            throw new BusinessException("La conexión con Tiendanube necesita volver a autorizarse");
        }

        return store;
    }

    private TiendanubeStore requireUsableStore(TiendanubeProductLink link) {
        TiendanubeStore store = storeRepository.findByStoreIdAndActiveTrue(link.getTiendanubeStoreId())
                .orElseThrow(() -> new BusinessException("La cuenta Tiendanube vinculada ya no está activa"));

        if (!store.isTokenValid()) {
            throw new BusinessException("La conexión con Tiendanube necesita volver a autorizarse");
        }

        return store;
    }

    private boolean canQueueLinkedOperation(TiendanubeProductLink link, TiendanubeJobType type) {
        if (type == TiendanubeJobType.DELETE_PUBLICATION) {
            return true;
        }

        TiendanubeInventoryStatus status = link.getInventory().getTiendanubeStatus();
        return status == TiendanubeInventoryStatus.LINKED || status == TiendanubeInventoryStatus.SYNC_ERROR;
    }

    private boolean isUsable(TiendanubeStore store) {
        return store != null && store.isActive() && store.isTokenValid();
    }

    private boolean isUnsafeUntilStage3(TiendanubeJobType type) {
        return type == TiendanubeJobType.PUBLISH
                || type == TiendanubeJobType.SYNC_PUBLICATION
                || type == TiendanubeJobType.DELETE_PUBLICATION;
    }

    private void validateLinkedType(TiendanubeJobType type) {
        if (type == TiendanubeJobType.PUBLISH) {
            throw new IllegalArgumentException("PUBLISH debe encolarse mediante enqueuePublish");
        }
    }
}
