package com.rodrilang.librarymanager.shipping.shipment.tracking;

import java.util.List;

public record ProviderTrackingSnapshot(
        String providerStatus,
        String providerStatusDetail,
        String trackingUrl,
        List<ProviderTrackingEvent> events
) {}
