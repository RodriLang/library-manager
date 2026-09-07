package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairCandidate;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairRequestResult;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationRepairItemService {

    private final TiendanubeReconciliationRepository reconciliationRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeJobRequestService jobRequestService;

    @Transactional
    public TiendanubeReconciliationRepairRequestResult requestRepair(
            Long runId,
            Long itemId,
            Long bookstoreId,
            TiendanubeReconciliationRepairSource source
    ) {
        TiendanubeReconciliationRepairCandidate candidate = reconciliationRepository
                .findRepairCandidateForUpdate(runId, itemId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el hallazgo de reconciliación"));

        if (!isRepairable(candidate.issueType())) {
            return TiendanubeReconciliationRepairRequestResult.MANUAL_REVIEW;
        }

        if (candidate.repairRequestedAt() != null) {
            return TiendanubeReconciliationRepairRequestResult.ALREADY_REQUESTED;
        }

        requireExpectedStore(candidate);
        TiendanubeProductLink link = requireExpectedLink(candidate);

        if (candidate.issueType() == TiendanubeReconciliationIssueType.PRICE_MISMATCH
                && !Boolean.TRUE.equals(link.getInventory().getTiendanubePriceSyncEnabled())) {
            throw new BusinessException("La sincronización de precio fue deshabilitada después de la reconciliación");
        }

        TiendanubeJobType jobType = switch (candidate.issueType()) {
            case STOCK_MISMATCH -> TiendanubeJobType.SYNC_STOCK;
            case PRICE_MISMATCH -> TiendanubeJobType.SYNC_PRICE;
            case REMOTE_PRODUCT_MISSING, REMOTE_VARIANT_MISSING -> throw new IllegalStateException(
                    "Los recursos remotos faltantes requieren revisión manual"
            );
        };

        Long jobId = enqueueRepair(candidate.inventoryId(), jobType, source);

        boolean updated = reconciliationRepository.markRepairQueued(candidate.itemId(), source, jobId, Instant.now());

        if (!updated) {
            throw new IllegalStateException("El hallazgo ya fue tomado por otra solicitud de reparación");
        }

        return TiendanubeReconciliationRepairRequestResult.QUEUED;
    }

    private Long enqueueRepair(
            Long inventoryId,
            TiendanubeJobType jobType,
            TiendanubeReconciliationRepairSource source
    ) {
        if (source == TiendanubeReconciliationRepairSource.MANUAL) {
            return jobRequestService.enqueueManualLinked(inventoryId, jobType);
        }

        return jobRequestService.enqueueAutomaticLinked(inventoryId, jobType)
                .orElseThrow(() -> new BusinessException("No se pudo encolar la reparación automática de Tiendanube"));
    }

    private TiendanubeStore requireExpectedStore(TiendanubeReconciliationRepairCandidate candidate) {
        TiendanubeStore store = storeRepository.findById(candidate.tiendanubeStoreId())
                .orElseThrow(() -> new BusinessException("La conexión Tiendanube de la reconciliación ya no existe"));

        if (!store.isActive()) {
            throw new BusinessException("La cuenta Tiendanube de la reconciliación ya no está activa");
        }

        if (!store.isTokenValid()) {
            throw new BusinessException("La conexión con Tiendanube necesita volver a autorizarse");
        }

        if (!Objects.equals(store.getStoreId(), candidate.storeId())
                || !Objects.equals(store.getBookstore().getId(), candidate.bookstoreId())) {
            throw new BusinessException("La cuenta Tiendanube actual no coincide con la reconciliación");
        }

        return store;
    }

    private TiendanubeProductLink requireExpectedLink(TiendanubeReconciliationRepairCandidate candidate) {
        TiendanubeProductLink link = productLinkRepository.findByInventoryIdAndActiveTrue(candidate.inventoryId())
                .orElseThrow(() -> new BusinessException("El inventario ya no tiene un vínculo activo con Tiendanube"));

        if (!Objects.equals(link.getId(), candidate.linkId())
                || !Objects.equals(link.getTiendanubeStoreId(), candidate.storeId())
                || !Objects.equals(link.getTiendanubeProductId(), candidate.productId())
                || !Objects.equals(link.getTiendanubeVariantId(), candidate.variantId())) {
            throw new BusinessException("El vínculo Tiendanube cambió después de la reconciliación");
        }

        return link;
    }

    private boolean isRepairable(TiendanubeReconciliationIssueType issueType) {
        return issueType == TiendanubeReconciliationIssueType.STOCK_MISMATCH
                || issueType == TiendanubeReconciliationIssueType.PRICE_MISMATCH;
    }
}
