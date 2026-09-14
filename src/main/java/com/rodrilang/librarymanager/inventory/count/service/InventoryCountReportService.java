package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountReportSummaryResponse;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryCountReportService {

    private final InventoryCountResultRepository resultRepository;
    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountApplyService applyService;

    public InventoryCountReportSummaryResponse build(InventoryCountSession session) {
        long previousUnits = resultRepository.sumPreviousQuantity(session.getId());
        long countedUnits = resultRepository.sumCountedQuantity(session.getId());
        long comparedTitles = resultRepository.countBySessionIdAndCountedQuantityIsNotNull(session.getId());

        long matched = count(session, InventoryCountDifferenceType.MATCH);
        long surplus = count(session, InventoryCountDifferenceType.SURPLUS);
        long shortage = count(session, InventoryCountDifferenceType.SHORTAGE);
        long missing = count(session, InventoryCountDifferenceType.MISSING);
        long newTitles = count(session, InventoryCountDifferenceType.NEW);
        long added = count(session, InventoryCountDifferenceType.ADDED);
        long netDifference = session.getMode() == InventoryCountMode.ADDITIVE
                ? countedUnits
                : countedUnits - previousUnits;

        return new InventoryCountReportSummaryResponse(
                comparedTitles,
                matched,
                surplus,
                shortage,
                missing,
                newTitles,
                added,
                previousUnits,
                countedUnits,
                netDifference,
                itemRepository.countBySessionIdAndStatus(session.getId(), InventoryCountItemStatus.PENDING_CATALOG),
                itemRepository.countBySessionIdAndStatus(session.getId(), InventoryCountItemStatus.PENDING_PRICE),
                itemRepository.countBySessionIdAndStatus(session.getId(), InventoryCountItemStatus.INVALID_IDENTIFIER),
                itemRepository.countBySessionIdAndStatus(session.getId(), InventoryCountItemStatus.SUPERSEDED),
                applyService.countConcurrentMovements(session)
        );
    }

    private long count(InventoryCountSession session, InventoryCountDifferenceType type) {
        return resultRepository.countBySessionIdAndDifferenceType(session.getId(), type);
    }
}
