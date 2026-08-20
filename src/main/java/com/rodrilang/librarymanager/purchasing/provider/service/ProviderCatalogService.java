package com.rodrilang.librarymanager.purchasing.provider.service;

import com.rodrilang.librarymanager.purchasing.provider.dto.ProviderCatalogFilter;
import com.rodrilang.librarymanager.purchasing.provider.dto.response.ProviderCatalogBookResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProviderCatalogService {

    Page<ProviderCatalogBookResponse> findAll(
            Long providerId,
            ProviderCatalogFilter filter,
            Pageable pageable
    );
}