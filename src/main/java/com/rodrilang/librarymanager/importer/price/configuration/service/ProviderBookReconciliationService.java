package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookReconciliationPreview;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookReconciliationResult;

public interface ProviderBookReconciliationService {

    ProviderBookReconciliationPreview preview(Long providerBookId, Long targetBookId);

    ProviderBookReconciliationResult confirm(Long providerBookId, Long targetBookId);
}