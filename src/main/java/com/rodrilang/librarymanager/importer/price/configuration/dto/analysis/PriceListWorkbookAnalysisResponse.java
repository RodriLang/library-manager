package com.rodrilang.librarymanager.importer.price.configuration.dto.analysis;

import java.util.List;

public record PriceListWorkbookAnalysisResponse(

        String fileName,

        List<PriceListSheetAnalysisResponse> sheets

) {
}