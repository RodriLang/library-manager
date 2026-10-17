package com.rodrilang.librarymanager.shipping.provider;

import com.rodrilang.librarymanager.shipping.model.ShippingCapability;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;

import java.util.Set;

/**
 * Stable extension point for carrier-specific adapters.
 * Stage 1 only exposes metadata/capabilities; quote/create/track operations are added behind this SPI in later stages.
 */
public interface ShippingProvider {
    ShippingProviderType type();
    String displayName();
    boolean requiresCredentials();
    Set<ShippingCapability> capabilities();
}
