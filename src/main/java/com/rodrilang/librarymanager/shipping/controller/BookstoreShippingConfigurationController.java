package com.rodrilang.librarymanager.shipping.controller;

import com.rodrilang.librarymanager.shipping.dto.*;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.shipping.correoargentino.CorreoArgentinoConnectionService;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPoint;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPointClientRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/shipping")
@RequiredArgsConstructor
public class BookstoreShippingConfigurationController {
    private final BookstoreShippingConfigurationService service;
    private final CorreoArgentinoConnectionService correoArgentinoConnectionService;
    private final ShippingPickupPointClientRegistry pickupPointClientRegistry;

    @GetMapping
    public ShippingConfigurationResponse current() {
        return service.current();
    }

    @PutMapping("/settings")
    public ShippingSettingsResponse updateSettings(@Valid @RequestBody UpdateShippingSettingsRequest request) {
        return service.updateSettings(request);
    }

    @PutMapping("/integrations/{provider}")
    public ShippingIntegrationResponse updateIntegration(
            @PathVariable ShippingProviderType provider,
            @Valid @RequestBody UpdateShippingIntegrationRequest request
    ) {
        return service.updateIntegration(provider, request);
    }


    @GetMapping("/integrations/{provider}/services")
    public java.util.List<ShippingServiceResponse> services(@PathVariable ShippingProviderType provider) {
        return service.services(provider);
    }

    @PutMapping("/integrations/{provider}/services/{serviceCode}")
    public ShippingServiceResponse updateService(
            @PathVariable ShippingProviderType provider,
            @PathVariable String serviceCode,
            @Valid @RequestBody UpdateShippingServiceRequest request
    ) {
        return service.updateService(provider, serviceCode, request);
    }

    @GetMapping("/integrations/{provider}/pickup-points")
    public java.util.List<ShippingPickupPoint> pickupPoints(@PathVariable ShippingProviderType provider, @RequestParam String postalCode) {
        return pickupPointClientRegistry.require(provider).findByPostalCode(postalCode);
    }

    @PostMapping("/integrations/CORREO_ARGENTINO/test")
    public ShippingConnectionTestResponse testCorreoArgentino() {
        return correoArgentinoConnectionService.test();
    }

    @PostMapping("/integrations/{provider}/disconnect")
    public ShippingIntegrationResponse disconnect(@PathVariable ShippingProviderType provider) {
        return service.disconnect(provider);
    }
}
