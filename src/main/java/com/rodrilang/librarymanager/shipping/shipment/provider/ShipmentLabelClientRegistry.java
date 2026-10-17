package com.rodrilang.librarymanager.shipping.shipment.provider;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ShipmentLabelClientRegistry {
    private final Map<ShippingProviderType, ShipmentLabelClient> clients;
    public ShipmentLabelClientRegistry(java.util.List<ShipmentLabelClient> clients) {
        EnumMap<ShippingProviderType, ShipmentLabelClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(c -> map.put(c.provider(), c)); this.clients = Map.copyOf(map);
    }
    public Optional<ShipmentLabelClient> find(ShippingProviderType provider) { return Optional.ofNullable(clients.get(provider)); }
}
