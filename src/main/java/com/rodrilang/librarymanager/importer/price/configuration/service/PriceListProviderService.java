package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.CreatePriceListProviderRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListProviderResponse;

import java.util.List;

public interface PriceListProviderService {

    PriceListProviderResponse create(CreatePriceListProviderRequest request);

    List<PriceListProviderResponse> findAllActive();

    PriceListProviderResponse findById(Long id);
}