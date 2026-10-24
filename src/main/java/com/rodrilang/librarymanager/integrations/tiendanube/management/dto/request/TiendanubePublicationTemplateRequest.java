package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record TiendanubePublicationTemplateRequest(boolean enabled, @NotNull @Size(max=4000) String commercialMessage, boolean includeIsbn) {}
