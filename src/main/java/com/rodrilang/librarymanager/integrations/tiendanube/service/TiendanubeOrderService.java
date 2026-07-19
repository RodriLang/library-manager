package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;

public interface TiendanubeOrderService {

    void handleOrderPaid(TiendanubeWebhookRequest request);

    void handleOrderCancelled(TiendanubeWebhookRequest request);
}