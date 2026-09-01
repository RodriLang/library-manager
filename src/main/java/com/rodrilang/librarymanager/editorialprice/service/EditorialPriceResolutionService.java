package com.rodrilang.librarymanager.editorialprice.service;

import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceResolutionListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EditorialPriceResolutionService {

    Page<EditorialPriceResolutionListResponse> findAll(Boolean active, String query, Pageable pageable);
}