package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubePrivacyWebhookRequest;

public interface TiendanubePrivacyWebhookService {

    void handleStoreRedact(TiendanubePrivacyWebhookRequest request);

    void handleCustomersRedact(TiendanubePrivacyWebhookRequest request);

    void handleCustomersDataRequest(TiendanubePrivacyWebhookRequest request);
}
