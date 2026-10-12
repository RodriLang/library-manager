package com.rodrilang.librarymanager.shipping.shipment.tracking;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ShipmentTrackingClientRegistry {
    private final Map<ShippingProviderType, ShipmentTrackingClient> clients;

    public ShipmentTrackingClientRegistry(List<ShipmentTrackingClient> clients) {
        EnumMap<ShippingProviderType, ShipmentTrackingClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(client -> map.put(client.provider(), client));
        this.clients = Map.copyOf(map);
    }

    public Optional<ShipmentTrackingClient> find(ShippingProviderType provider) {
        return Optional.ofNullable(clients.get(provider));
    }
}
