package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

public record TiendanubePossibleDuplicateResponse(Long productId, String title, String reason, boolean published) {}
