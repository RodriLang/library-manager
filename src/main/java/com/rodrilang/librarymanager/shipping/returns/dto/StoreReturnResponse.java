package com.rodrilang.librarymanager.shipping.returns.dto;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturnStatus;
import java.time.Instant;
import java.util.List;
public record StoreReturnResponse(Long id, Long shipmentId, StoreReturnStatus status, String reason, String notes,
                                  ShippingProviderType provider, String providerReturnId, String trackingNumber, String trackingUrl,
                                  Instant requestedAt, Instant approvedAt, Instant receivedAt, Instant completedAt, Instant cancelledAt,
                                  List<StoreReturnItemResponse> items) {}
