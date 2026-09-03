package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TiendanubeJobWorkerExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void execute(Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
