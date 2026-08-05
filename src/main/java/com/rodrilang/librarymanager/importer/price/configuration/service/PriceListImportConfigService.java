package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.request.CreatePriceListImportConfigRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.PriceListImportConfigResponse;

public interface PriceListImportConfigService {

    PriceListImportConfigResponse create(Long providerId, CreatePriceListImportConfigRequest request);

    PriceListImportConfigResponse findActiveByProvider(Long providerId);
}