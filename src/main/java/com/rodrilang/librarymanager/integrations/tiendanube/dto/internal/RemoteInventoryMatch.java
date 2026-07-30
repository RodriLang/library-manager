package com.rodrilang.librarymanager.integrations.tiendanube.dto.internal;

public record RemoteInventoryMatch(
        Long productId,
        Long variantId,
        boolean autoLink
) {
}