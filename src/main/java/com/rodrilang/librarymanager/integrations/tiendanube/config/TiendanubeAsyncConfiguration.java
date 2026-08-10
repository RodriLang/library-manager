package com.rodrilang.librarymanager.integrations.tiendanube.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class TiendanubeAsyncConfiguration {

    @Bean(name = "tiendanubePriceSyncExecutor")
    public Executor tiendanubePriceSyncExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix(
                "tiendanube-price-sync-"
        );

        executor.initialize();

        return executor;
    }
}