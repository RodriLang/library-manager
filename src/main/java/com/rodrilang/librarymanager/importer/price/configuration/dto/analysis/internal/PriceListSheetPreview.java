package com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.internal;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;

import java.util.List;

public record PriceListSheetPreview(
        int sheetIndex,
        String sheetName,
        int observedRowCount,
        int columnCount,
        boolean truncated,
        List<PriceListPreviewRowResponse> rows
) {
}