package com.rodrilang.librarymanager.store.dto;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record UpdateStoreSectionsOrderRequest(@NotNull List<Long> sectionIds) {}
