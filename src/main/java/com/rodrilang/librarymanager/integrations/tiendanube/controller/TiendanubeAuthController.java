package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/integrations/tiendanube")
@RequiredArgsConstructor
public class TiendanubeAuthController {

    private final TiendanubeOAuthService oAuthService;

    @GetMapping("/install")
    public ResponseEntity<Void> install() {
        String authorizationUrl = oAuthService.buildAuthorizationUrl();

        return ResponseEntity
                .status(302)
                .location(URI.create(authorizationUrl))
                .build();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<String> callback(@RequestParam String code) {
        oAuthService.handleCallback(code);

        return ResponseEntity.ok("Tienda conectada correctamente.");
    }
}