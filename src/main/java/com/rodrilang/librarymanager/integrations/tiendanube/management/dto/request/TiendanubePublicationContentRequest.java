package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contenido editorial y comercial en texto plano. No admite HTML arbitrario. */
public record TiendanubePublicationContentRequest(
        @NotNull @Size(max = 20000) String synopsis,
        @NotNull @Size(max = 4000) String commercialMessage,
        boolean includeIsbn
) {}
