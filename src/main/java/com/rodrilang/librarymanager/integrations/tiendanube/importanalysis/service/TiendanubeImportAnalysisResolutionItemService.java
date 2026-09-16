package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemLock;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisItemWriteRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisResolutionItemService {

    private final TiendanubeImportAnalysisItemWriteRepository itemWriteRepository;
    private final TiendanubeImportAnalysisRunRepository runRepository;
    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeJobRequestService jobRequestService;

    @Transactional
    public Long resolve(
            Long runId,
            Long itemId,
            Long bookstoreId,
            Long inventoryId,
            boolean syncStock,
            boolean refreshCounts
    ) {
        TiendanubeImportAnalysisItemLock item = itemWriteRepository
                .lockItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new BusinessException(
                        "No se encontró un caso resoluble para el análisis indicado"
                ));

        validateItemStatus(item.status());

        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));

        validateInventory(inventory, bookstoreId);
        validateCanLink(item, inventory);

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(item.storeId())
                .tiendanubeProductId(item.productId())
                .tiendanubeVariantId(item.variantId())
                .sku(item.remoteSku())
                .active(true)
                .lastSyncedAt(null)
                .lastError(null)
                .build();

        productLinkRepository.save(link);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);

        Instant now = Instant.now();

        if (!itemWriteRepository.markResolved(item.id(), inventory.getId(), now)) {
            throw new BusinessException("El caso ya fue resuelto por otro proceso");
        }

        itemWriteRepository.markInventoryUnavailableForOtherItems(
                runId,
                item.id(),
                inventory.getId(),
                now
        );

        if (refreshCounts) {
            runRepository.refreshCounts(runId);
        }

        if (syncStock) {
            jobRequestService.enqueueAutomaticLinked(inventory.getId(), TiendanubeJobType.SYNC_STOCK);
        }

        return inventory.getId();
    }

    @Transactional
    public void refreshCounts(Long runId) {
        runRepository.refreshCounts(runId);
    }

    @Transactional
    public void ignore(Long runId, Long itemId, Long bookstoreId) {
        if (!itemWriteRepository.markIgnored(runId, itemId, bookstoreId, Instant.now())) {
            throw new BusinessException("El caso no puede ignorarse o ya fue resuelto");
        }

        runRepository.refreshCounts(runId);
    }

    private void validateItemStatus(TiendanubeImportAnalysisItemStatus status) {
        if (status == TiendanubeImportAnalysisItemStatus.RESOLVED
                || status == TiendanubeImportAnalysisItemStatus.IGNORED
                || status == TiendanubeImportAnalysisItemStatus.ALREADY_LINKED) {
            throw new BusinessException("El caso ya está cerrado y no puede volver a vincularse");
        }
    }

    private void validateInventory(Inventory inventory, Long bookstoreId) {
        if (!bookstoreId.equals(inventory.getBookstore().getId())) {
            throw new BusinessException("El inventario seleccionado pertenece a otra librería");
        }

        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException("El inventario seleccionado está inactivo");
        }

        if (inventory.getCondition() != BookCondition.NEW) {
            throw new BusinessException("Tiendanube solo puede vincularse con inventario en condición NEW");
        }

        if (!Boolean.TRUE.equals(inventory.getBook().getActive())) {
            throw new BusinessException("El libro seleccionado está inactivo en el catálogo");
        }
    }

    private void validateCanLink(
            TiendanubeImportAnalysisItemLock item,
            Inventory inventory
    ) {
        if (productLinkRepository
                .findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventory.getId(), item.storeId())
                .isPresent()) {
            throw new BusinessException("El inventario ya está vinculado con otra publicación de Tiendanube");
        }

        if (productLinkRepository
                .findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(item.storeId(), item.variantId())
                .isPresent()) {
            throw new BusinessException("La variante de Tiendanube ya está vinculada a otro inventario");
        }
    }
}
