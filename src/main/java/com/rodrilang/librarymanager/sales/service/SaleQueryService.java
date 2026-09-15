package com.rodrilang.librarymanager.sales.service;

import com.rodrilang.librarymanager.sales.dto.SaleFilter;
import com.rodrilang.librarymanager.sales.dto.response.SaleDetailResponse;
import com.rodrilang.librarymanager.sales.dto.response.SaleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SaleQueryService {

    Page<SaleResponse> findAll(SaleFilter filter, Pageable pageable);

    SaleDetailResponse findById(Long saleId);
}
