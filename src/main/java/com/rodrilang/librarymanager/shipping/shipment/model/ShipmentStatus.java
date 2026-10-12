package com.rodrilang.librarymanager.shipping.shipment.model;

public enum ShipmentStatus {
    DRAFT,
    READY_TO_SHIP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    CANCELLED
}
