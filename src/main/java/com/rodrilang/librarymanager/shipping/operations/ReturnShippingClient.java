package com.rodrilang.librarymanager.shipping.operations;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturn;
public interface ReturnShippingClient {
    ShippingProviderType provider();
    ReturnShippingResult createReturn(StoreReturn storeReturn);
}
