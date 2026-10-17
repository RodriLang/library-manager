package com.rodrilang.librarymanager.admin.integration.mercadopago.controller;

import com.rodrilang.librarymanager.admin.integration.mercadopago.dto.AdminMercadoPagoPlatformConfigResponse;
import com.rodrilang.librarymanager.admin.integration.mercadopago.dto.UpdateAdminMercadoPagoPlatformConfigRequest;
import com.rodrilang.librarymanager.admin.integration.mercadopago.service.MercadoPagoPlatformConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/integrations/mercado-pago")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMercadoPagoPlatformConfigController {
    private final MercadoPagoPlatformConfigService service;

    @GetMapping
    public AdminMercadoPagoPlatformConfigResponse get() {
        return service.adminView();
    }

    @PutMapping
    public AdminMercadoPagoPlatformConfigResponse update(@Valid @RequestBody UpdateAdminMercadoPagoPlatformConfigRequest request) {
        return service.update(request);
    }
}
