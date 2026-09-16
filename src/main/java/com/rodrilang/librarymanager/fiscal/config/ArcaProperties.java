package com.rodrilang.librarymanager.fiscal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "arca")
public record ArcaProperties(

        boolean enabled,
        ArcaEnvironment environment,
        String delegateCuit,
        String certificateBase64,
        String privateKeyBase64,
        String service,
        BigDecimal consumerFinalIdentificationThreshold,
        String wsaaHomologationUrl,
        String wsaaProductionUrl,
        String wsfeHomologationUrl,
        String wsfeProductionUrl

) {
    public String wsaaUrl() {
        return environment == ArcaEnvironment.PRODUCTION
                ? wsaaProductionUrl
                : wsaaHomologationUrl;
    }

    public String wsfeUrl() {
        return environment == ArcaEnvironment.PRODUCTION
                ? wsfeProductionUrl
                : wsfeHomologationUrl;
    }
}
