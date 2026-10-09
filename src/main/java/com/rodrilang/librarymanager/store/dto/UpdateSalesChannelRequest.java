package com.rodrilang.librarymanager.store.dto;
import jakarta.validation.constraints.NotNull;
public record UpdateSalesChannelRequest(@NotNull Boolean enabled) {}
