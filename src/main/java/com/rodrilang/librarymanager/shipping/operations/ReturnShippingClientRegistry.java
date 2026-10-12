package com.rodrilang.librarymanager.shipping.operations;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ReturnShippingClientRegistry {
    private final Map<ShippingProviderType, ReturnShippingClient> clients;

    public ReturnShippingClientRegistry(List<ReturnShippingClient> clients) {
        EnumMap<ShippingProviderType, ReturnShippingClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(client -> map.put(client.provider(), client));
        this.clients = Map.copyOf(map);
    }

    public Optional<ReturnShippingClient> find(ShippingProviderType provider) {
        return Optional.ofNullable(clients.get(provider));
    }
}
