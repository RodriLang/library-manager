package com.rodrilang.librarymanager.store.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CreateStoreDomainRequest(@NotBlank @Size(max=255) String hostname) {}
