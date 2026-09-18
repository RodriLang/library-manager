package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.fiscal.dto.request.UpdateFiscalSettingsRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalSettingsResponse;

public interface BookstoreFiscalSettingsService {

    FiscalSettingsResponse get();

    FiscalSettingsResponse update(UpdateFiscalSettingsRequest request);

    FiscalSettingsResponse verifyAuthorization();
}
