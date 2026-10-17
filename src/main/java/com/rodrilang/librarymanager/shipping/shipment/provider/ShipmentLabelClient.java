package com.rodrilang.librarymanager.shipping.shipment.provider;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
public interface ShipmentLabelClient {
    ShippingProviderType provider();
    ShipmentLabelResult label(StoreShipment shipment);
}
