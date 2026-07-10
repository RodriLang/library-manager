package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;

public interface TiendanubeWebhookService {

    void process(TiendanubeWebhookRequest request);
}