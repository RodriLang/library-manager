package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationDataService {

    private final TiendanubeReconciliationRepository reconciliationRepository;

    @Transactional(readOnly = true)
    public List<TiendanubeReconciliationInventorySnapshot> load(TiendanubeClaimedReconciliationRun run) {
        return reconciliationRepository.findInventorySnapshots(run.bookstoreId(), run.storeId());
    }
}
