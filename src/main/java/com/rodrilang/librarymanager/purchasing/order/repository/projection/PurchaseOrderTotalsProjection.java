package com.rodrilang.librarymanager.purchasing.order.repository.projection;

import java.math.BigDecimal;

public interface PurchaseOrderTotalsProjection {

    Long getOrderId();

    Long getItemCount();

    Long getTotalUnits();

    BigDecimal getEstimatedTotal();
}