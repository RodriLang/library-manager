package com.rodrilang.librarymanager.sales.repository.projection;

public interface SaleItemsSummaryProjection {

    Long getSaleId();

    Long getItemCount();

    Long getTotalUnits();
}
