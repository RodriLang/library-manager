package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeDeletePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
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
public class TiendanubeDeletePublicationJobHandler implements TiendanubeJobHandler {

    private final TiendanubeJobConnectionGuard connectionGuard;
    private final TiendanubeJobExecutionDataService dataService;
    private final TiendanubeJobResultService resultService;
    private final TiendanubeClient client;

    @Override
    public TiendanubeJobType type() {
        return TiendanubeJobType.DELETE_PUBLICATION;
    }

    @Override
    public void execute(TiendanubeJobExecutionContext context) {
        connectionGuard.validate(context);
        Optional<TiendanubeDeletePublicationSnapshot> prepared = dataService.prepareDelete(
                context.inventoryId(), context.storeId()
        );

        if (prepared.isEmpty()) {
            resultService.markNotPublished(context.inventoryId());
            return;
        }

        TiendanubeDeletePublicationSnapshot snapshot = prepared.get();

        try {
            client.deleteProduct(snapshot.storeId(), snapshot.productId());
            resultService.registerDeleteSuccess(snapshot.inventoryId(), snapshot.linkId());

            log.info("Publicación eliminada de Tiendanube. inventoryId={} productId={}",
                    snapshot.inventoryId(), snapshot.productId());
        } catch (TiendanubeRemoteResourceNotFoundException exception) {
            resultService.registerDeleteSuccess(snapshot.inventoryId(), snapshot.linkId());
            log.info("Publicación remota ya no existía; se completó la desvinculación local. inventoryId={} productId={}",
                    snapshot.inventoryId(), snapshot.productId());
        } catch (RuntimeException exception) {
            resultService.registerFailure(snapshot.inventoryId(), snapshot.linkId(), exception);
            throw exception;
        }
    }
}
