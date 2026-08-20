package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TiendanubeUpdateProductRequest(
        Map<String, String> name,
        Map<String, String> description
) {
}