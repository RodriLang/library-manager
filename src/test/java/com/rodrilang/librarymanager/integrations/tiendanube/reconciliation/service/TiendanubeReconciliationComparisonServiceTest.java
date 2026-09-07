package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationIssue;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TiendanubeReconciliationComparisonServiceTest {

    private final TiendanubeReconciliationComparisonService service = new TiendanubeReconciliationComparisonService();

    @Test
    void healthyPublicationHasNoIssues() {
        TiendanubeReconciliationInventorySnapshot local = snapshot(2, "24000.00", true);
        TiendanubeVariantResponse variant = variant(30L, 2, "24000");
        TiendanubeProductResponse product = product(20L, List.of(variant));

        assertTrue(service.compare(List.of(local), List.of(product)).isEmpty());
    }

    @Test
    void detectsMissingProduct() {
        List<TiendanubeReconciliationIssue> issues = service.compare(
                List.of(snapshot(2, "24000.00", true)),
                List.of()
        );

        assertEquals(List.of(TiendanubeReconciliationIssueType.REMOTE_PRODUCT_MISSING), types(issues));
    }

    @Test
    void detectsMissingVariant() {
        TiendanubeProductResponse product = product(20L, List.of(variant(99L, 2, "24000")));

        List<TiendanubeReconciliationIssue> issues = service.compare(
                List.of(snapshot(2, "24000.00", true)),
                List.of(product)
        );

        assertEquals(List.of(TiendanubeReconciliationIssueType.REMOTE_VARIANT_MISSING), types(issues));
    }

    @Test
    void detectsStockMismatch() {
        TiendanubeProductResponse product = product(20L, List.of(variant(30L, 5, "24000")));

        List<TiendanubeReconciliationIssue> issues = service.compare(
                List.of(snapshot(2, "24000.00", true)),
                List.of(product)
        );

        assertEquals(List.of(TiendanubeReconciliationIssueType.STOCK_MISMATCH), types(issues));
        assertEquals(2, issues.getFirst().localStock());
        assertEquals(5, issues.getFirst().remoteStock());
    }

    @Test
    void detectsPriceMismatchWhenPriceSyncEnabled() {
        TiendanubeProductResponse product = product(20L, List.of(variant(30L, 2, "22000")));

        List<TiendanubeReconciliationIssue> issues = service.compare(
                List.of(snapshot(2, "24000.00", true)),
                List.of(product)
        );

        assertEquals(List.of(TiendanubeReconciliationIssueType.PRICE_MISMATCH), types(issues));
    }

    @Test
    void ignoresPriceMismatchWhenPriceSyncDisabled() {
        TiendanubeProductResponse product = product(20L, List.of(variant(30L, 2, "22000")));

        List<TiendanubeReconciliationIssue> issues = service.compare(
                List.of(snapshot(2, "24000.00", false)),
                List.of(product)
        );

        assertTrue(issues.isEmpty());
    }

    private TiendanubeReconciliationInventorySnapshot snapshot(int stock, String price, boolean priceSyncEnabled) {
        return new TiendanubeReconciliationInventorySnapshot(
                10L,
                11L,
                20L,
                30L,
                stock,
                new BigDecimal(price),
                priceSyncEnabled
        );
    }

    private TiendanubeProductResponse product(Long id, List<TiendanubeVariantResponse> variants) {
        TiendanubeProductResponse product = mock(TiendanubeProductResponse.class);
        when(product.id()).thenReturn(id);
        when(product.variants()).thenReturn(variants);
        return product;
    }

    private TiendanubeVariantResponse variant(Long id, int stock, String price) {
        TiendanubeVariantResponse variant = mock(TiendanubeVariantResponse.class);
        when(variant.id()).thenReturn(id);
        when(variant.stock()).thenReturn(stock);
        when(variant.price()).thenReturn(new BigDecimal(price));
        return variant;
    }

    private List<TiendanubeReconciliationIssueType> types(List<TiendanubeReconciliationIssue> issues) {
        return issues.stream().map(TiendanubeReconciliationIssue::issueType).toList();
    }
}
