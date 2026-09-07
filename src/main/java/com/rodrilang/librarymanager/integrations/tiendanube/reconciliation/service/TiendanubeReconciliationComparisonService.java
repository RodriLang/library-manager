package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationIssue;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TiendanubeReconciliationComparisonService {

    public List<TiendanubeReconciliationIssue> compare(
            List<TiendanubeReconciliationInventorySnapshot> localSnapshots,
            List<TiendanubeProductResponse> remoteProducts
    ) {
        Map<Long, TiendanubeProductResponse> productsById = indexProducts(remoteProducts);
        List<TiendanubeReconciliationIssue> issues = new ArrayList<>();

        for (TiendanubeReconciliationInventorySnapshot local : localSnapshots) {
            TiendanubeProductResponse product = productsById.get(local.productId());

            if (product == null) {
                issues.add(issue(
                        local,
                        TiendanubeReconciliationIssueType.REMOTE_PRODUCT_MISSING,
                        null,
                        null,
                        "La publicación vinculada ya no existe en Tiendanube"
                ));
                continue;
            }

            TiendanubeVariantResponse variant = findVariant(product, local.variantId());

            if (variant == null) {
                issues.add(issue(
                        local,
                        TiendanubeReconciliationIssueType.REMOTE_VARIANT_MISSING,
                        null,
                        null,
                        "La variante vinculada ya no existe dentro de la publicación de Tiendanube"
                ));
                continue;
            }

            if (!Objects.equals(local.localStock(), variant.stock())) {
                issues.add(issue(
                        local,
                        TiendanubeReconciliationIssueType.STOCK_MISMATCH,
                        variant.stock(),
                        variant.price(),
                        "El stock de Anaquel no coincide con el stock informado por Tiendanube"
                ));
            }

            if (local.priceSyncEnabled() && !samePrice(local.localPrice(), variant.price())) {
                issues.add(issue(
                        local,
                        TiendanubeReconciliationIssueType.PRICE_MISMATCH,
                        variant.stock(),
                        variant.price(),
                        "El precio de Anaquel no coincide con Tiendanube y la sincronización de precio está habilitada"
                ));
            }
        }

        return issues;
    }

    private Map<Long, TiendanubeProductResponse> indexProducts(List<TiendanubeProductResponse> products) {
        Map<Long, TiendanubeProductResponse> result = new HashMap<>();

        if (products == null) {
            return result;
        }

        for (TiendanubeProductResponse product : products) {
            if (product != null && product.id() != null) {
                result.put(product.id(), product);
            }
        }

        return result;
    }

    private TiendanubeVariantResponse findVariant(TiendanubeProductResponse product, Long variantId) {
        if (product.variants() == null) {
            return null;
        }

        return product.variants().stream()
                .filter(variant -> variant != null && Objects.equals(variant.id(), variantId))
                .findFirst()
                .orElse(null);
    }

    private boolean samePrice(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }

        return left.compareTo(right) == 0;
    }

    private TiendanubeReconciliationIssue issue(
            TiendanubeReconciliationInventorySnapshot local,
            TiendanubeReconciliationIssueType type,
            Integer remoteStock,
            BigDecimal remotePrice,
            String message
    ) {
        return new TiendanubeReconciliationIssue(
                local.inventoryId(),
                local.linkId(),
                local.productId(),
                local.variantId(),
                type,
                local.localStock(),
                remoteStock,
                local.localPrice(),
                remotePrice,
                message
        );
    }
}
