package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeCoverSyncResult;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubePublicationSyncService {

    private final TiendanubeClient client;
    private final TiendanubeCoverSyncService coverSyncService;
    private final TiendanubeJobResultService resultService;


    public void sync(TiendanubePublicationSnapshot snapshot) {
        var linked = snapshot.linkedInventory();

        try {
            // Descripciones editadas manualmente o generadas por la plantilla no se sobrescriben al sincronizar.
            var metadata = snapshot.productRequest();
            client.updateProduct(linked.storeId(), linked.productId(),
                    new com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest(metadata.name(), null));
            client.updateVariant(linked.storeId(), linked.productId(), linked.variantId(), linked.fullVariantRequest());

            TiendanubeCoverSyncResult cover = coverSyncService.sync(
                    linked.linkId(),
                    linked.storeId(),
                    linked.productId(),
                    snapshot.coverUrl(),
                    snapshot.lastSyncedCoverUrl(),
                    snapshot.lastSyncedImageId(),
                    snapshot.pendingCoverUrl(),
                    snapshot.pendingCoverExistingImageIds()
            );

            resultService.registerPublicationSuccess(linked, cover);

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
