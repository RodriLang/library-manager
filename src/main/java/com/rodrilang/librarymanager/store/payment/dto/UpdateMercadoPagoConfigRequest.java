package com.rodrilang.librarymanager.store.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMercadoPagoConfigRequest(
        @NotNull Boolean enabled,
        @Size(max = 2000) String accessToken,
        @Size(max = 2000) String webhookSecret
) {}
