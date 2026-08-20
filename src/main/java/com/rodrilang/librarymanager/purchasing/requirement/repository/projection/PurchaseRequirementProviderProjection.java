package com.rodrilang.librarymanager.purchasing.requirement.repository.projection;

import java.math.BigDecimal;

public interface PurchaseRequirementProviderProjection {

    Long getBookId();

    Long getProviderId();

    String getProviderName();

    BigDecimal getPrice();
}