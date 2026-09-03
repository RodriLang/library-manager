package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeLinkedInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobExecutionDataService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubePriceJobHandler implements TiendanubeJobHandler {

    private final TiendanubeJobConnectionGuard connectionGuard;
    private final TiendanubeJobExecutionDataService dataService;
    private final TiendanubeJobResultService resultService;
    private final TiendanubeClient client;

    @Override
    public TiendanubeJobType type() {
        return TiendanubeJobType.SYNC_PRICE;
    }

    @Override
    public void execute(TiendanubeJobExecutionContext context) {
        connectionGuard.validate(context);
        Optional<TiendanubeLinkedInventorySnapshot> prepared = dataService.prepareLinkedInventory(
                context.inventoryId(), context.storeId()
        );

        if (prepared.isEmpty()) {
            log.info("Price sync job omitted because the inventory is no longer linked. inventoryId={}", context.inventoryId());
            return;
        }

        TiendanubeLinkedInventorySnapshot snapshot = prepared.get();
        TiendanubeUpdateVariantRequest request = new TiendanubeUpdateVariantRequest(
                null, null, snapshot.salePrice(), null, null, null, null, null, null
        );

        try {
            client.updateVariant(snapshot.storeId(), snapshot.productId(), snapshot.variantId(), request);
            resultService.registerLinkedSuccess(snapshot);

            log.info("Precio sincronizado con Tiendanube. inventoryId={} price={}",
                    snapshot.inventoryId(), snapshot.salePrice());
        } catch (TiendanubeRemoteResourceNotFoundException exception) {
            resultService.registerRemoteNotFound(snapshot.inventoryId(), snapshot.linkId(), exception);
            throw TiendanubeJobExecutionException.nonRetryable(
                    "REMOTE_RESOURCE_NOT_FOUND", exception.getMessage(), exception
            );
        } catch (RuntimeException exception) {
            resultService.registerFailure(snapshot.inventoryId(), snapshot.linkId(), exception);
            throw exception;
        }
    }
}
