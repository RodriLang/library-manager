package com.rodrilang.librarymanager.provider.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderTest {

    @Test
    void activeCommercialProviderIsPurchasable() {
        Provider provider = Provider.builder()
                .type(ProviderType.COMMERCIAL)
                .active(true)
                .build();

        assertTrue(provider.isPurchasable());
    }

    @Test
    void systemProviderIsNotPurchasable() {
        Provider provider = Provider.builder()
                .type(ProviderType.SYSTEM)
                .active(true)
                .build();

        assertFalse(provider.isPurchasable());
    }

    @Test
    void inactiveCommercialProviderIsNotPurchasable() {
        Provider provider = Provider.builder()
                .type(ProviderType.COMMERCIAL)
                .active(false)
                .build();

        assertFalse(provider.isPurchasable());
    }
}
