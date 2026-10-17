package com.rodrilang.librarymanager.shipping.shipment.dto;
import java.util.List;
public record CreateStoreShipmentRequest(List<ShipmentItemAllocationRequest> items, String notes) {}
