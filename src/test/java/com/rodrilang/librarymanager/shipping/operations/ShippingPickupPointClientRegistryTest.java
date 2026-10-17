package com.rodrilang.librarymanager.shipping.operations;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShippingPickupPointClientRegistryTest {
    @Test
    void requiresOnlyProvidersWithPickupPointAdapter() {
        ShippingPickupPointClient oca = new ShippingPickupPointClient() {
            @Override public ShippingProviderType provider() { return ShippingProviderType.OCA; }
            @Override public List<ShippingPickupPoint> findByPostalCode(Long bookstoreId, String postalCode) { return List.of(); }
        };
        ShippingPickupPointClientRegistry registry = new ShippingPickupPointClientRegistry(List.of(oca));
        assertSame(oca, registry.require(ShippingProviderType.OCA));
        assertThrows(IllegalArgumentException.class, () -> registry.require(ShippingProviderType.ANDREANI));
    }
}
