package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.ProviderBookReconciliationPreview;
import com.rodrilang.librarymanager.importer.price.configuration.dto.ProviderBookReconciliationResult;

public interface ProviderBookReconciliationService {

    ProviderBookReconciliationPreview preview(Long providerBookId, Long targetBookId);

    ProviderBookReconciliationResult confirm(Long providerBookId, Long targetBookId);
}