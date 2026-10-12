package com.rodrilang.librarymanager.shipping.operations;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import java.util.List;
public interface ShippingPickupPointClient {
    ShippingProviderType provider();
    List<ShippingPickupPoint> findByPostalCode(String postalCode);
}
