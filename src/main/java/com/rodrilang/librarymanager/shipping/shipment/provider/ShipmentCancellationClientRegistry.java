package com.rodrilang.librarymanager.shipping.shipment.provider;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ShipmentCancellationClientRegistry {
    private final Map<ShippingProviderType, ShipmentCancellationClient> clients;
    public ShipmentCancellationClientRegistry(java.util.List<ShipmentCancellationClient> clients) {
        EnumMap<ShippingProviderType, ShipmentCancellationClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(c -> map.put(c.provider(), c)); this.clients = Map.copyOf(map);
    }
    public Optional<ShipmentCancellationClient> find(ShippingProviderType provider) { return Optional.ofNullable(clients.get(provider)); }
}
