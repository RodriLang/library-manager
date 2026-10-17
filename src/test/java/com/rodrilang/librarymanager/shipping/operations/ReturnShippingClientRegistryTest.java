package com.rodrilang.librarymanager.shipping.operations;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReturnShippingClientRegistryTest {
    @Test
    void exposesConfiguredProviderOnly() {
        ReturnShippingClient oca = new ReturnShippingClient() {
            @Override public ShippingProviderType provider() { return ShippingProviderType.OCA; }
            @Override public ReturnShippingResult createReturn(StoreReturn storeReturn) { return null; }
        };
        ReturnShippingClientRegistry registry = new ReturnShippingClientRegistry(List.of(oca));
        assertTrue(registry.find(ShippingProviderType.OCA).isPresent());
        assertTrue(registry.find(ShippingProviderType.CORREO_ARGENTINO).isEmpty());
    }
}
