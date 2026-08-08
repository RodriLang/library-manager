package com.rodrilang.librarymanager.repository.projection;

import java.math.BigDecimal;

public interface EditorialPriceImportProjection {

    Long getId();

    Long getBookId();

    BigDecimal getPrice();

    Boolean getActive();
}