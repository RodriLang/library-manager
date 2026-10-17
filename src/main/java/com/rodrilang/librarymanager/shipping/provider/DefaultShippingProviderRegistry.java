package com.rodrilang.librarymanager.shipping.provider;

import com.rodrilang.librarymanager.shipping.model.ShippingCapability;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DefaultShippingProviderRegistry {
    private final Map<ShippingProviderType, ShippingProvider> providers;

    public DefaultShippingProviderRegistry() {
        EnumMap<ShippingProviderType, ShippingProvider> map = new EnumMap<>(ShippingProviderType.class);
        register(map, descriptor(ShippingProviderType.ANDREANI, "Andreani", true,
                ShippingCapability.QUOTE, ShippingCapability.CREATE_SHIPMENT, ShippingCapability.CANCEL_SHIPMENT,
                ShippingCapability.TRACKING, ShippingCapability.LABEL, ShippingCapability.BRANCHES));
        register(map, descriptor(ShippingProviderType.CORREO_ARGENTINO, "Correo Argentino", true,
                ShippingCapability.QUOTE, ShippingCapability.CREATE_SHIPMENT, ShippingCapability.CANCEL_SHIPMENT,
                ShippingCapability.TRACKING, ShippingCapability.LABEL, ShippingCapability.BRANCHES, ShippingCapability.PICKUP_POINTS));
        register(map, descriptor(ShippingProviderType.OCA, "OCA", true,
                ShippingCapability.QUOTE, ShippingCapability.CREATE_SHIPMENT, ShippingCapability.CANCEL_SHIPMENT,
                ShippingCapability.TRACKING, ShippingCapability.LABEL, ShippingCapability.BRANCHES,
                ShippingCapability.RETURNS));
        register(map, descriptor(ShippingProviderType.MANUAL, "Envío manual", false, ShippingCapability.QUOTE));
        register(map, descriptor(ShippingProviderType.PICKUP_IN_STORE, "Retiro en librería", false, ShippingCapability.QUOTE));
        providers = Collections.unmodifiableMap(map);
    }

    public List<ShippingProvider> all() {
        return Arrays.stream(ShippingProviderType.values()).map(this::get).toList();
    }

    public ShippingProvider get(ShippingProviderType type) {
        ShippingProvider provider = providers.get(type);
        if (provider == null) throw new IllegalArgumentException("Proveedor de envío no soportado: " + type);
        return provider;
    }

    private void register(Map<ShippingProviderType, ShippingProvider> target, ShippingProvider provider) {
        target.put(provider.type(), provider);
    }

    private ShippingProvider descriptor(ShippingProviderType type, String name, boolean credentials, ShippingCapability... capabilities) {
        Set<ShippingCapability> set = capabilities.length == 0
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(capabilities)));
        return new ShippingProvider() {
            @Override public ShippingProviderType type() { return type; }
            @Override public String displayName() { return name; }
            @Override public boolean requiresCredentials() { return credentials; }
            @Override public Set<ShippingCapability> capabilities() { return set; }
        };
    }
}
