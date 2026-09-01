package com.rodrilang.librarymanager.importer.price.repository.projection;

import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;

public interface PriceListImportPriceChangeCountProjection {

    Long getJobId();

    EditorialPriceChange getPriceChange();

    long getTotal();
}