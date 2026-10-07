package com.rodrilang.librarymanager.inventory.consignment.dto;

import com.rodrilang.librarymanager.inventory.consignment.model.ConsignmentSettlementStatus;

import java.time.Instant;

public record ConsignmentSettlementResponse(
        Long id,
        String settlementNumber,
        Long providerId,
        String providerName,
        ConsignmentSettlementStatus status,
        Instant settledAt,
        String notes,
        Integer itemCount,
        Integer totalUnits
) {}
