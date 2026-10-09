package com.rodrilang.librarymanager.store.payment.controller;

import com.rodrilang.librarymanager.admin.integration.mercadopago.service.MercadoPagoPlatformConfigService;
import com.rodrilang.librarymanager.store.payment.dto.MercadoPagoAuthorizationResponse;
import com.rodrilang.librarymanager.store.payment.dto.MercadoPagoConfigResponse;
import com.rodrilang.librarymanager.store.payment.dto.UpdateMercadoPagoEnabledRequest;
import com.rodrilang.librarymanager.store.payment.service.StoreMercadoPagoConfigService;
import com.rodrilang.librarymanager.store.payment.service.StoreMercadoPagoOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/store/payments/mercado-pago")
@RequiredArgsConstructor
public class StoreMercadoPagoConfigController {
    private final StoreMercadoPagoConfigService configService;
    private final StoreMercadoPagoOAuthService oauthService;
    private final MercadoPagoPlatformConfigService platformConfigService;

    @GetMapping
    public MercadoPagoConfigResponse get() {
        return configService.current();
    }

    @GetMapping("/authorization-url")
    public MercadoPagoAuthorizationResponse authorizationUrl() {
        return oauthService.createAuthorizationUrl();
    }

    @PutMapping("/enabled")
    public MercadoPagoConfigResponse enabled(@Valid @RequestBody UpdateMercadoPagoEnabledRequest request) {
        return configService.setEnabled(Boolean.TRUE.equals(request.enabled()));
    }

    @DeleteMapping("/disconnect")
    public MercadoPagoConfigResponse disconnect() {
        return configService.disconnect();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        boolean connected = false;
        String message = null;
        try {
            if (error != null && !error.isBlank()) {
                message = errorDescription == null || errorDescription.isBlank() ? error : errorDescription;
            } else {
                oauthService.handleCallback(code, state);
                connected = true;
            }
        } catch (RuntimeException ex) {
            message = "No pudimos completar la conexión con Mercado Pago. Intentá nuevamente.";
        }

        UriComponentsBuilder redirect = UriComponentsBuilder
                .fromUriString(normalizeFrontend(platformConfigService.frontendUrl()))
                .path("/store/payments")
                .queryParam("mercadoPago", connected ? "connected" : "error");
        if (!connected && message != null && !message.isBlank()) {
            redirect.queryParam("message", message.length() > 180 ? message.substring(0, 180) : message);
        }
        URI uri = redirect.build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }

    private String normalizeFrontend(String frontendUrl) {
        String value = frontendUrl == null || frontendUrl.isBlank() ? "https://anaquel.com.ar" : frontendUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
