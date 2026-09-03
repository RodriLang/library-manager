package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublishSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobExecutionDataService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobResultService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubePublicationSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubePublishJobHandler implements TiendanubeJobHandler {

    private final TiendanubeJobConnectionGuard connectionGuard;
    private final TiendanubeJobExecutionDataService dataService;
    private final TiendanubeJobResultService resultService;
    private final TiendanubeProductMatchingService matchingService;
    private final TiendanubePublicationSyncService publicationSyncService;
    private final TiendanubeClient client;

    @Override
    public TiendanubeJobType type() {
        return TiendanubeJobType.PUBLISH;
    }

    @Override
    public void execute(TiendanubeJobExecutionContext context) {
        connectionGuard.validate(context);
        Optional<TiendanubePublishSnapshot> prepared = dataService.preparePublish(context.inventoryId(), context.storeId());

        if (prepared.isEmpty()) {
            syncLinkedPublication(context);
            return;
        }

        TiendanubePublishSnapshot snapshot = prepared.get();
        PublishResolution resolution;

        try {
            resolution = recoverByStableSku(snapshot);

            if (resolution == PublishResolution.NOT_FOUND) {
                resolution = resolvePreexistingPublication(snapshot);
            }

            if (resolution == PublishResolution.REVIEW_REQUIRED) {
                return;
            }

            if (resolution == PublishResolution.NOT_FOUND) {
                resultService.markPublishing(snapshot.inventoryId());
                TiendanubeProductResponse product = client.createProduct(
                        snapshot.storeId(),
                        withoutImages(snapshot.productRequest())
                );
                TiendanubeVariantResponse variant = getMainVariant(product);

                resultService.savePublishedLink(
                        snapshot.inventoryId(),
                        snapshot.storeId(),
                        product,
                        variant,
                        null,
                        snapshot.variantRequest().sku()
                );

                log.info("Inventario publicado en Tiendanube. inventoryId={} productId={} variantId={}",
                        snapshot.inventoryId(), product.id(), variant.id());
            }
        } catch (RuntimeException exception) {
            resultService.registerFailure(snapshot.inventoryId(), null, exception);
            throw exception;
        }

        syncLinkedPublication(context);
    }

    private PublishResolution recoverByStableSku(TiendanubePublishSnapshot snapshot) {
        String sku = snapshot.variantRequest().sku();
        Optional<TiendanubeProductResponse> existing = client.findProductBySku(snapshot.storeId(), sku);

        if (existing.isEmpty()) {
            return PublishResolution.NOT_FOUND;
        }

        TiendanubeProductResponse product = existing.get();
        TiendanubeVariantResponse variant = findVariantBySku(product, sku);
        resultService.validateVariantAvailable(snapshot.storeId(), variant.id(), snapshot.inventoryId());
        resultService.saveExistingLink(snapshot.inventoryId(), snapshot.storeId(), product.id(), variant, sku);

        log.info("Publicación Tiendanube recuperada por SKU estable. inventoryId={} productId={} variantId={} sku={}",
                snapshot.inventoryId(), product.id(), variant.id(), sku);
        return PublishResolution.LINKED;
    }

    private PublishResolution resolvePreexistingPublication(TiendanubePublishSnapshot snapshot) {
        String searchQuery = resolveSearchQuery(snapshot);

        if (searchQuery == null || searchQuery.isBlank()) {
            return PublishResolution.NOT_FOUND;
        }

        List<TiendanubeProductResponse> candidates = client.searchProducts(snapshot.storeId(), searchQuery);
        RemoteInventoryMatch match = matchingService.findRemoteMatch(snapshot.isbn(), snapshot.titleSearch(), candidates);

        if (match == null) {
            return PublishResolution.NOT_FOUND;
        }

        if (!match.autoLink()) {
            resultService.markLinkRequired(snapshot.inventoryId());
            log.info("Publicación Tiendanube requiere revisión manual. inventoryId={} matchType={}",
                    snapshot.inventoryId(), match.matchType());
            return PublishResolution.REVIEW_REQUIRED;
        }

        TiendanubeProductResponse product = findProduct(candidates, match.productId());
        TiendanubeVariantResponse variant = TiendanubeProductUtils.findVariant(product, match.variantId());
        resultService.validateVariantAvailable(snapshot.storeId(), variant.id(), snapshot.inventoryId());
        resultService.saveExistingLink(
                snapshot.inventoryId(), snapshot.storeId(), product.id(), variant, snapshot.variantRequest().sku()
        );

        log.info("Inventario vinculado con publicación existente. inventoryId={} productId={} variantId={}",
                snapshot.inventoryId(), product.id(), variant.id());
        return PublishResolution.LINKED;
    }

    private void syncLinkedPublication(TiendanubeJobExecutionContext context) {
        TiendanubePublicationSnapshot publication = dataService.preparePublication(context.inventoryId(), context.storeId())
                .orElseThrow(() -> new IllegalStateException(
                        "El inventario quedó publicado pero no se encontró un vínculo activo para completar la sincronización"
                ));

        publicationSyncService.sync(publication);
    }

    private TiendanubeCreateProductRequest withoutImages(TiendanubeCreateProductRequest request) {
        return new TiendanubeCreateProductRequest(
                request.name(), request.description(), request.variants(), List.of(), request.published()
        );
    }

    private String resolveSearchQuery(TiendanubePublishSnapshot snapshot) {
        if (snapshot.productRequest().name() == null || snapshot.productRequest().name().isEmpty()) {
            return snapshot.titleSearch();
        }

        return snapshot.productRequest().name().getOrDefault("es", snapshot.titleSearch());
    }

    private TiendanubeVariantResponse findVariantBySku(TiendanubeProductResponse product, String sku) {
        if (product.variants() == null || product.variants().isEmpty()) {
            throw new IllegalStateException("Tiendanube devolvió un producto sin variantes para el SKU " + sku);
        }

        String normalizedSku = TiendanubeProductUtils.normalizeIdentifier(sku);

        return product.variants().stream()
                .filter(variant -> normalizedSku.equals(TiendanubeProductUtils.normalizeIdentifier(variant.sku())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Tiendanube devolvió un producto que no contiene la variante esperada para el SKU " + sku
                ));
    }

    private TiendanubeProductResponse findProduct(List<TiendanubeProductResponse> products, Long productId) {
        return products.stream()
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró el producto remoto seleccionado"));
    }

    private TiendanubeVariantResponse getMainVariant(TiendanubeProductResponse product) {
        if (product == null || product.variants() == null || product.variants().isEmpty()) {
            throw new IllegalStateException("Tiendanube creó el producto sin variantes");
        }

        return product.variants().getFirst();
    }

    private enum PublishResolution {
        NOT_FOUND,
        LINKED,
        REVIEW_REQUIRED
    }
}
