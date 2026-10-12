package com.rodrilang.librarymanager.shipping.operations;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturn;
import com.rodrilang.librarymanager.shipping.shipment.provider.ShipmentLabelResult;

public interface ReturnShippingLabelClient {
    ShippingProviderType provider();
    ShipmentLabelResult label(StoreReturn storeReturn);
}
