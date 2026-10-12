package com.rodrilang.librarymanager.shipping.operations;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import java.util.List;
public interface ShippingManifestClient {
    ShippingProviderType provider();
    ShippingManifestResult createManifest(List<StoreShipment> shipments);
}
