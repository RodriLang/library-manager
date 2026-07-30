package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubePrivacyWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubePrivacyWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tiendanube - Privacidad", description = "Webhooks de privacidad y protección de datos requeridos por Tiendanube")
@RestController
@RequestMapping("/api/integrations/tiendanube/webhooks/privacy")
@RequiredArgsConstructor
public class TiendanubePrivacyWebhookController {

    private final TiendanubePrivacyWebhookService privacyWebhookService;

    @PostMapping("/store-redact")
    public ResponseEntity<Void> handleStoreRedact(
            @RequestBody TiendanubePrivacyWebhookRequest request
    ) {
        privacyWebhookService.handleStoreRedact(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/customers-redact")
    public ResponseEntity<Void> handleCustomersRedact(
            @RequestBody TiendanubePrivacyWebhookRequest request
    ) {
        privacyWebhookService.handleCustomersRedact(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/customers-data-request")
    public ResponseEntity<Void> handleCustomersDataRequest(
            @RequestBody TiendanubePrivacyWebhookRequest request
    ) {
        privacyWebhookService.handleCustomersDataRequest(request);
        return ResponseEntity.ok().build();
    }
}