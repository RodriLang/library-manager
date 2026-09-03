package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobExecutionDataService;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubePublicationSyncService;
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
    private final TiendanubePublicationSyncService publicationSyncService;

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

        publicationSyncService.sync(prepared.get());
    }
}
