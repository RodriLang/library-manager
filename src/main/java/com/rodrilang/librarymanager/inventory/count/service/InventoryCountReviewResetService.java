package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryCountReviewResetService {

    private final InventoryCountResultRepository resultRepository;

    public void resetToOpen(InventoryCountSession session) {
        if (session.getStatus() != InventoryCountStatus.REVIEW) {
            return;
        }

        resetReviewData(session);
        session.setStatus(InventoryCountStatus.OPEN);
        session.setReviewedAt(null);
    }

    public void resetReviewData(InventoryCountSession session) {
        resultRepository.deleteUnappliedNonBaselineBySessionId(session.getId());

        List<InventoryCountResult> baseline = resultRepository.findAllBySessionIdOrderById(session.getId()).stream()
                .filter(InventoryCountResult::isBaseline)
                .toList();

        baseline.forEach(result -> {
            result.setCountedQuantity(null);
            result.setAppliedDelta(0);
            result.setResultingQuantity(null);
            result.setResultingActive(null);
            result.setDifferenceType(null);
            result.setAppliedAt(null);
            result.setRevertedAt(null);
        });
    }
}
