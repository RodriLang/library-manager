package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeCoverSyncResult;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeCoverSyncService;
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
public class TiendanubePublicationJobHandler implements TiendanubeJobHandler {

    private final TiendanubeJobConnectionGuard connectionGuard;
    private final TiendanubeJobExecutionDataService dataService;
    private final TiendanubeJobResultService resultService;
    private final TiendanubeCoverSyncService coverSyncService;
    private final TiendanubeClient client;

    @Override
    public TiendanubeJobType type() {
        return TiendanubeJobType.SYNC_PUBLICATION;
    }

    @Override
    public void execute(TiendanubeJobExecutionContext context) {
        connectionGuard.validate(context);
        Optional<TiendanubePublicationSnapshot> prepared = dataService.preparePublication(
                context.inventoryId(), context.storeId()
        );

        if (prepared.isEmpty()) {
            log.info("Publication sync job omitted because the inventory is no longer linked. inventoryId={}",
                    context.inventoryId());
            return;
        }

        TiendanubePublicationSnapshot snapshot = prepared.get();
        var linked = snapshot.linkedInventory();

        try {
            client.updateProduct(linked.storeId(), linked.productId(), snapshot.productRequest());
            client.updateVariant(linked.storeId(), linked.productId(), linked.variantId(), linked.fullVariantRequest());

            TiendanubeCoverSyncResult cover = coverSyncService.sync(
                    linked.storeId(),
                    linked.productId(),
                    snapshot.coverUrl(),
                    snapshot.lastSyncedCoverUrl()
            );

            resultService.registerPublicationSuccess(linked, cover.coverUrl(), cover.imageId());

            log.info("Publicación sincronizada con Tiendanube. inventoryId={} productId={}",
                    linked.inventoryId(), linked.productId());
        } catch (TiendanubeRemoteResourceNotFoundException exception) {
            resultService.registerRemoteNotFound(linked.inventoryId(), linked.linkId(), exception);
            throw TiendanubeJobExecutionException.nonRetryable(
                    "REMOTE_RESOURCE_NOT_FOUND", exception.getMessage(), exception
            );
        } catch (RuntimeException exception) {
            resultService.registerFailure(linked.inventoryId(), linked.linkId(), exception);
            throw exception;
        }
    }
}
