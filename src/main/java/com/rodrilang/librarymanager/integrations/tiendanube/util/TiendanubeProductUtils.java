package com.rodrilang.librarymanager.integrations.tiendanube.util;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;

public final class TiendanubeProductUtils {

    private TiendanubeProductUtils() {
    }

    public static TiendanubeVariantResponse findVariant(TiendanubeProductResponse product, Long variantId) {
        if (product.variants() == null) {
            throw new BusinessException("El producto de Tiendanube no posee variantes");
        }

        return product.variants().stream()
                .filter(variant -> variant.id().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "La variante " + variantId + " no pertenece al producto " + product.id()
                ));
    }

    public static String resolveRemoteIsbn(TiendanubeVariantResponse variant) {
        String barcode = normalizeIdentifier(variant.barcode());

        if (isValidIsbn(barcode)) {
            return barcode;
        }

        String sku = normalizeIdentifier(variant.sku());

        return isValidIsbn(sku) ? sku : null;
    }

    public static String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.replace("-", "").replace(" ", "").trim();
    }

    private static boolean isValidIsbn(String value) {
        return value != null
                && (value.length() == 10 || value.length() == 13)
                && value.chars().allMatch(Character::isDigit);
    }
}