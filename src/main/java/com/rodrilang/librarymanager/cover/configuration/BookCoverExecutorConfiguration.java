package com.rodrilang.librarymanager.cover.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class BookCoverExecutorConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService bookCoverExecutor(
            BookCoverProcessingProperties properties
    ) {
        return Executors.newFixedThreadPool(
                properties.parallelism()
        );
    }
}
