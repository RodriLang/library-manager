package com.rodrilang.librarymanager.dto.internal;

import com.rodrilang.librarymanager.enums.EditorialPriceChangeType;
import com.rodrilang.librarymanager.model.EditorialPrice;

public record EditorialPriceImportResult(

        EditorialPrice editorialPrice,

        EditorialPriceChangeType changeType
) {
}
