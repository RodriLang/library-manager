package com.rodrilang.librarymanager.importer.price.configuration.dto.analysis;

import java.util.List;

public record PriceListPreviewRowResponse(

        Integer rowIndex,

        List<String> cells

) {
}