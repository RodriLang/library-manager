package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.request.CreatePriceListProviderRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.PriceListProviderResponse;

import java.util.List;

public interface PriceListProviderService {

    PriceListProviderResponse create(CreatePriceListProviderRequest request);

    List<PriceListProviderResponse> findAllActive();

    PriceListProviderResponse findById(Long id);
}