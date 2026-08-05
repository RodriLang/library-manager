package com.rodrilang.librarymanager.importer.price.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class PriceListImportAsyncConfig {

    private final PriceListImportProperties properties;

    @Bean(name = "priceListImportExecutor")
    public Executor priceListImportExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(
                properties.maxConcurrentImports()
        );

        executor.setMaxPoolSize(
                properties.maxConcurrentImports()
        );

        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("price-import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}