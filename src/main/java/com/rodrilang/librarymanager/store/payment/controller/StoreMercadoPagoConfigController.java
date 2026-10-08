package com.rodrilang.librarymanager.store.payment.controller;

import com.rodrilang.librarymanager.store.payment.dto.MercadoPagoConfigResponse;
import com.rodrilang.librarymanager.store.payment.dto.UpdateMercadoPagoConfigRequest;
import com.rodrilang.librarymanager.store.payment.service.StoreMercadoPagoConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/payments/mercado-pago")
@RequiredArgsConstructor
public class StoreMercadoPagoConfigController {
    private final StoreMercadoPagoConfigService service;

    @GetMapping
    public MercadoPagoConfigResponse get() { return service.current(); }

    @PutMapping
    public MercadoPagoConfigResponse update(@Valid @RequestBody UpdateMercadoPagoConfigRequest request) {
        return service.update(request);
    }
}
