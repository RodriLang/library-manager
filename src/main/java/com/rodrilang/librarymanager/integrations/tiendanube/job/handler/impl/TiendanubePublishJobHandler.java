package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublishSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobExecutionDataService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobResultService;
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
            resultService.markAlreadyLinked(context.inventoryId());
            log.info("Publish job omitted because the inventory is already linked. inventoryId={}", context.inventoryId());
            return;
        }

        TiendanubePublishSnapshot snapshot = prepared.get();

        try {
            List<TiendanubeProductResponse> remoteProducts = client.getProducts(snapshot.storeId());
            RemoteInventoryMatch match = matchingService.findRemoteMatch(
                    snapshot.isbn(), snapshot.titleSearch(), remoteProducts
            );

            if (match != null) {
                if (!match.autoLink()) {
                    resultService.markLinkRequired(snapshot.inventoryId());
                    return;
                }

                TiendanubeProductResponse product = findProduct(remoteProducts, match.productId());
                TiendanubeVariantResponse variant = TiendanubeProductUtils.findVariant(product, match.variantId());
                resultService.validateVariantAvailable(snapshot.storeId(), variant.id(), snapshot.inventoryId());

                client.updateVariant(snapshot.storeId(), product.id(), variant.id(), snapshot.variantRequest());
                resultService.saveExistingLink(
                        snapshot.inventoryId(),
                        snapshot.storeId(),
                        product.id(),
                        variant,
                        snapshot.variantRequest().sku()
                );

                log.info("Inventario vinculado con publicación existente. inventoryId={} productId={} variantId={}",
                        snapshot.inventoryId(), product.id(), variant.id());
                return;
            }

            resultService.markPublishing(snapshot.inventoryId());

            TiendanubeProductResponse product = client.createProduct(snapshot.storeId(), snapshot.productRequest());
            TiendanubeVariantResponse variant = getMainVariant(product);

            resultService.savePublishedLink(
                    snapshot.inventoryId(),
                    snapshot.storeId(),
                    product,
                    variant,
                    snapshot.coverUrl(),
                    snapshot.variantRequest().sku()
            );

            log.info("Inventario publicado en Tiendanube. inventoryId={} productId={} variantId={}",
                    snapshot.inventoryId(), product.id(), variant.id());
        } catch (RuntimeException exception) {
            resultService.registerFailure(snapshot.inventoryId(), null, exception);
            throw exception;
        }
    }

    private TiendanubeProductResponse findProduct(List<TiendanubeProductResponse> products, Long productId) {
        return products.stream()
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró el producto remoto seleccionado"));
    }

    private TiendanubeVariantResponse getMainVariant(TiendanubeProductResponse product) {
        if (product.variants() == null || product.variants().isEmpty()) {
            throw new IllegalStateException("Tiendanube creó el producto sin variantes");
        }

        return product.variants().getFirst();
    }
}
