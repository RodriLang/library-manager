package com.rodrilang.librarymanager.importer.price.configuration.dto.analysis;

import java.util.List;

public record PriceListSheetAnalysisResponse(

        Integer index,

        String name,

        Integer rowCount,

        Integer columnCount,

        Integer suggestedHeaderRowIndex,

        List<PriceListPreviewRowResponse> previewRows

) {
}