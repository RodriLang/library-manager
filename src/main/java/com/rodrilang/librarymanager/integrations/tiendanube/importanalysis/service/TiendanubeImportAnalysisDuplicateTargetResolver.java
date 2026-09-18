package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TiendanubeImportAnalysisDuplicateTargetResolver {

    public List<TiendanubeImportAnalysisItemData> resolve(List<TiendanubeImportAnalysisItemData> items) {
        Map<Long, Long> readyCountByInventory = items.stream()
                .filter(item -> item.status() == TiendanubeImportAnalysisItemStatus.READY_TO_LINK)
                .map(TiendanubeImportAnalysisItemData::suggestedInventoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return items.stream()
                .map(item -> hasDuplicateTarget(item, readyCountByInventory)
                        ? asConflict(item)
                        : item)
                .toList();
    }

    private boolean hasDuplicateTarget(
            TiendanubeImportAnalysisItemData item,
            Map<Long, Long> readyCountByInventory
    ) {
        return item.status() == TiendanubeImportAnalysisItemStatus.READY_TO_LINK
                && item.suggestedInventoryId() != null
                && readyCountByInventory.getOrDefault(item.suggestedInventoryId(), 0L) > 1;
    }

    private TiendanubeImportAnalysisItemData asConflict(TiendanubeImportAnalysisItemData item) {
        return new TiendanubeImportAnalysisItemData(
                item.productId(),
                item.variantId(),
                item.remoteName(),
                item.remoteSku(),
                item.remoteBarcode(),
                item.remoteIsbn(),
                item.identifierSource(),
                item.identifierRecovered(),
                item.remotePrice(),
                item.remoteStock(),
                item.remoteImageUrl(),
                item.remotePublished(),
                TiendanubeImportAnalysisItemStatus.CONFLICT,
                TiendanubeImportAnalysisMatchType.MULTIPLE_MATCHES,
                item.suggestedInventoryId(),
                item.suggestedBookId(),
                "Más de una publicación de Tiendanube apunta al mismo inventario. Elegí cuál debe conservar el vínculo.",
                item.candidates()
        );
    }
}
