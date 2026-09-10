package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.config.TiendanubeJobProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobEnqueueCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobEnqueueRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TiendanubeJobEnqueueService {

    private final TiendanubeSyncJobEnqueueRepository enqueueRepository;
    private final TiendanubeJobProperties properties;
    private final TiendanubeWorkNotifier workNotifier;

    @Transactional
    public Long enqueue(TiendanubeJobEnqueueCommand command) {
        validate(command);

        int maxAttempts = command.maxAttempts() == null ? properties.getDefaultMaxAttempts() : command.maxAttempts();

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser mayor o igual a 1");
        }

        Long jobId = enqueueRepository.enqueue(command, maxAttempts, Instant.now());
        workNotifier.notifyWork(TiendanubeWorkType.JOB);
        return jobId;
    }

    private void validate(TiendanubeJobEnqueueCommand command) {
        Objects.requireNonNull(command, "command no puede ser null");
        Objects.requireNonNull(command.bookstoreId(), "bookstoreId no puede ser null");
        Objects.requireNonNull(command.tiendanubeStoreId(), "tiendanubeStoreId no puede ser null");
        Objects.requireNonNull(command.storeId(), "storeId no puede ser null");
        Objects.requireNonNull(command.inventoryId(), "inventoryId no puede ser null");
        Objects.requireNonNull(command.type(), "type no puede ser null");
        Objects.requireNonNull(command.source(), "source no puede ser null");
    }
}
