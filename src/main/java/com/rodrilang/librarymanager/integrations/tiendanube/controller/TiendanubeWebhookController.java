package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeWebhookService;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.service.TiendanubeWebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Tiendanube - Webhooks", description = "Recepción durable de eventos enviados por Tiendanube")
@RestController
@RequestMapping("/api/integrations/tiendanube/webhooks")
@RequiredArgsConstructor
public class TiendanubeWebhookController {

    private static final String SIGNATURE_HEADER = "x-linkedstore-hmac-sha256";

    private final TiendanubeWebhookService webhookService;
    private final TiendanubeWebhookSignatureVerifier signatureVerifier;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String payload
    ) {
        if (!signatureVerifier.isValid(payload, signature)) {
            log.warn("Webhook Tiendanube rechazado por firma inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.accept(payload);
        return ResponseEntity.accepted().build();
    }
}
