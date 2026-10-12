package com.rodrilang.librarymanager.shipping.operations;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ReturnShippingLabelClientRegistry {
    private final Map<ShippingProviderType, ReturnShippingLabelClient> clients;

    public ReturnShippingLabelClientRegistry(List<ReturnShippingLabelClient> clients) {
        EnumMap<ShippingProviderType, ReturnShippingLabelClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(client -> map.put(client.provider(), client));
        this.clients = Map.copyOf(map);
    }

    public Optional<ReturnShippingLabelClient> find(ShippingProviderType provider) {
        return Optional.ofNullable(clients.get(provider));
    }
}
