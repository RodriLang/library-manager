package com.rodrilang.librarymanager.importer.price.configuration.dto.analysis;

import java.util.List;

public record PriceListSheetAnalysisResponse(
        Integer sheetIndex,
        String sheetName,
        Integer previewedRowCount,
        Integer columnCount,
        Boolean previewTruncated,
        Integer suggestedHeaderRowIndex,
        List<PriceListSuggestedMappingResponse> suggestedMappings,
        List<PriceListPreviewRowResponse> preview
) {
}