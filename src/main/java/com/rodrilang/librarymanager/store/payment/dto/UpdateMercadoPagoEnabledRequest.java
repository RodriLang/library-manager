package com.rodrilang.librarymanager.store.payment.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMercadoPagoEnabledRequest(@NotNull Boolean enabled) {}
