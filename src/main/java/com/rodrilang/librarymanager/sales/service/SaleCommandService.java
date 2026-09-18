package com.rodrilang.librarymanager.sales.service;

import com.rodrilang.librarymanager.sales.dto.request.CancelSaleRequest;
import com.rodrilang.librarymanager.sales.dto.request.CreateSaleRequest;
import com.rodrilang.librarymanager.sales.dto.response.SaleDetailResponse;

public interface SaleCommandService {

    SaleDetailResponse create(CreateSaleRequest request);

    SaleDetailResponse cancel(Long saleId, CancelSaleRequest request);
}
