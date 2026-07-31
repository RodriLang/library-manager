package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.CreatePriceListImportConfigRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListImportConfigResponse;

public interface PriceListImportConfigService {

    PriceListImportConfigResponse create(Long providerId, CreatePriceListImportConfigRequest request);

    PriceListImportConfigResponse findActiveByProvider(Long providerId);
}