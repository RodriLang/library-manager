package com.rodrilang.librarymanager.integrations.tiendanube.job.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "tiendanube.jobs")
public class TiendanubeJobProperties {

    private boolean enabled = true;
    private int batchSize = 5;
    private int defaultMaxAttempts = 7;
    private Duration leaseDuration = Duration.ofMinutes(10);
}
