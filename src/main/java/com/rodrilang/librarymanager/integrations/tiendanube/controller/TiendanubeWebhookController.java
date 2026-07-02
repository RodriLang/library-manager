package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/tiendanube/webhooks")
@RequiredArgsConstructor
public class TiendanubeWebhookController {

    private final TiendanubeWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody TiendanubeWebhookRequest request) {
        webhookService.process(request);
        return ResponseEntity.ok().build();
    }
}