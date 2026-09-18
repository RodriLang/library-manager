package com.rodrilang.librarymanager.provider.service;

import com.rodrilang.librarymanager.provider.dto.request.CreateProviderRequest;
import com.rodrilang.librarymanager.provider.dto.request.UpdateProviderRequest;
import com.rodrilang.librarymanager.provider.dto.response.ProviderResponse;
import com.rodrilang.librarymanager.provider.model.ProviderType;

import java.util.List;

public interface ProviderService {

    ProviderResponse create(CreateProviderRequest request);

    ProviderResponse update(Long id, UpdateProviderRequest request);

    List<ProviderResponse> findAllActive(ProviderType type);

    ProviderResponse findById(Long id);
}
