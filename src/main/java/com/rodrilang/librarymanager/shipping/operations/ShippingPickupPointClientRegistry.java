package com.rodrilang.librarymanager.shipping.operations;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ShippingPickupPointClientRegistry {
    private final Map<ShippingProviderType, ShippingPickupPointClient> clients;
    public ShippingPickupPointClientRegistry(List<ShippingPickupPointClient> clients) {
        EnumMap<ShippingProviderType, ShippingPickupPointClient> map = new EnumMap<>(ShippingProviderType.class);
        clients.forEach(c -> map.put(c.provider(), c)); this.clients = Map.copyOf(map);
    }
    public ShippingPickupPointClient require(ShippingProviderType provider) {
        ShippingPickupPointClient c=clients.get(provider);
        if(c==null) throw new IllegalArgumentException("El proveedor no ofrece consulta de puntos de retiro.");
        return c;
    }
}
