package com.rodrilang.librarymanager.shipping.shipment.provider;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ShipmentCreationClientRegistry {
    private final Map<ShippingProviderType, ShipmentCreationClient> clients;
    public ShipmentCreationClientRegistry(java.util.List<ShipmentCreationClient> clients) {
        EnumMap<ShippingProviderType, ShipmentCreationClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(c -> map.put(c.provider(), c));
        this.clients = Map.copyOf(map);
    }
    public Optional<ShipmentCreationClient> find(ShippingProviderType provider) { return Optional.ofNullable(clients.get(provider)); }
}
