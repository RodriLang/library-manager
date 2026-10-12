package com.rodrilang.librarymanager.shipping.provider;

import com.rodrilang.librarymanager.shipping.model.ShippingCapability;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultShippingProviderRegistryTest {
    @Test
    void exposesAllStageOneProviders() {
        DefaultShippingProviderRegistry registry = new DefaultShippingProviderRegistry();

        assertEquals(5, registry.all().size());
        assertTrue(registry.get(ShippingProviderType.ANDREANI).requiresCredentials());
        assertTrue(registry.get(ShippingProviderType.ANDREANI).capabilities().contains(ShippingCapability.QUOTE));
        assertFalse(registry.get(ShippingProviderType.PICKUP_IN_STORE).requiresCredentials());
        assertTrue(registry.get(ShippingProviderType.MANUAL).capabilities().contains(ShippingCapability.QUOTE));
    }
}
