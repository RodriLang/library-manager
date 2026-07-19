package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeAuthorizationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/integrations/tiendanube")
@RequiredArgsConstructor
public class TiendanubeAuthController {

    private final TiendanubeOAuthService oAuthService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorization-url")
    public ResponseEntity<TiendanubeAuthorizationResponse>
    getAuthorizationUrl() {

        return ResponseEntity.ok(
                oAuthService.createAuthorizationUrl()
        );
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam String code,
                                         @RequestParam String state
    ) {
        oAuthService.handleCallback(code, state);

        URI redirectUri = URI.create(frontendUrl + "/settings/integrations/tiendanube" + "?connected=true");

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }
}