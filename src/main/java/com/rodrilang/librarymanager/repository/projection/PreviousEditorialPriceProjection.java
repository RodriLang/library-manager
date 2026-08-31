package com.rodrilang.librarymanager.repository.projection;

import java.math.BigDecimal;

public interface PreviousEditorialPriceProjection {

    Long getBookId();

    BigDecimal getPrice();
}