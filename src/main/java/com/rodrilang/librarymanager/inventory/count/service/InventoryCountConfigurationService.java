package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.count.dto.request.UpdateInventoryCountConfigurationRequest;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryCountConfigurationService {

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountReviewResetService reviewResetService;
    private final InventoryCountPendingApplyService pendingApplyService;
    private final ProviderRepository providerRepository;

    public void update(InventoryCountSession session, UpdateInventoryCountConfigurationRequest request) {
        requireConfigurable(session);

        if (request.publishOnTiendanube() != null) {
            session.setDefaultPublishOnTiendanube(request.publishOnTiendanube());
        }
        if (request.tiendanubePriceSyncEnabled() != null) {
            session.setDefaultTiendanubePriceSyncEnabled(request.tiendanubePriceSyncEnabled());
        }
        if (request.minimumStock() != null) {
            session.setDefaultMinimumStock(request.minimumStock());
        }
        if (request.consignment() != null) {
            session.setDefaultConsignment(request.consignment());
            if (Boolean.TRUE.equals(request.consignment())) {
                if (request.consignmentProviderId() == null) {
                    throw new BusinessException("Debe indicar el proveedor de consignación");
                }
                Provider provider = providerRepository.findById(request.consignmentProviderId())
                        .filter(Provider::isPurchasable)
                        .orElseThrow(() -> new BusinessException("El proveedor de consignación no existe o no está activo"));
                session.setDefaultConsignmentProvider(provider);
            } else {
                session.setDefaultConsignmentProvider(null);
            }
        }

        if (session.getStatus() == InventoryCountStatus.REVIEW) {
            reviewResetService.resetToOpen(session);
        }

        if (!request.applyToAll()) {
            return;
        }

        List<InventoryCountItem> items = itemRepository.findAllBySessionIdOrderById(session.getId());

        for (InventoryCountItem item : items) {
            if (item.getAppliedAt() != null || item.getStatus() == InventoryCountItemStatus.SUPERSEDED) {
                continue;
            }

            if (request.publishOnTiendanube() != null) {
                item.setPublishOnTiendanubeOverride(request.publishOnTiendanube());
            }
            if (request.tiendanubePriceSyncEnabled() != null) {
                item.setTiendanubePriceSyncOverride(request.tiendanubePriceSyncEnabled());
            }
            if (request.minimumStock() != null) {
                item.setMinimumStockOverride(request.minimumStock());
            }
            if (request.consignment() != null) {
                if (Boolean.TRUE.equals(request.consignment())) {
                    item.setConsignmentQuantityOverride(item.getQuantity());
                    item.setConsignmentProvider(session.getDefaultConsignmentProvider());
                } else {
                    item.setConsignmentQuantityOverride(0);
                    item.setConsignmentProvider(null);
                }
            }

            if (item.getBook() == null) {
                itemRepository.save(item);
                continue;
            }

            refreshStatus(item);
            itemRepository.save(item);

            if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING) {
                pendingApplyService.applyIfReady(item);
            }
        }
    }

    private void refreshStatus(InventoryCountItem item) {
        item.setStatus(InventoryCountItemStatus.RESOLVED);
    }

    private void requireConfigurable(InventoryCountSession session) {
        if (session.getStatus() == InventoryCountStatus.OPEN
                || session.getStatus() == InventoryCountStatus.REVIEW
                || session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING) {
            return;
        }
        throw new BusinessException("La configuración de esta carga ya no puede modificarse");
    }
}
