package com.rodrilang.librarymanager.shipping.shipment.tracking;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;

public interface ShipmentTrackingClient {
    ShippingProviderType provider();
    ProviderTrackingSnapshot track(StoreShipment shipment);
}
