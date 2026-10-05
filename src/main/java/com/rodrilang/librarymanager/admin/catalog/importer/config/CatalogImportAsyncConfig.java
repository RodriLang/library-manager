package com.rodrilang.librarymanager.admin.catalog.importer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
public class CatalogImportAsyncConfig {
    @Bean(name = "catalogImportExecutor")
    public Executor catalogImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("catalog-import-");
        executor.initialize();
        return executor;
    }
}
