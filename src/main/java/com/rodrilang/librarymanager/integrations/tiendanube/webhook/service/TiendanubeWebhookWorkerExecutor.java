package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TiendanubeWebhookWorkerExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void execute(Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.close();
    }
}
