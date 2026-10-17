package com.rodrilang.librarymanager.store.dto;
import jakarta.validation.constraints.*;
public record CreateStoreSectionRequest(@NotBlank @Size(max=150) String name) {}
