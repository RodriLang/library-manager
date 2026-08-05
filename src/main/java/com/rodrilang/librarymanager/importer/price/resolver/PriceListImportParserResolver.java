package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriceListImportParserResolver {

    private final StreamingConfigurablePriceListParser streamingConfigurableParser;

    public StreamingConfigurablePriceListParser resolveStreaming(
            PriceListImportJob job
    ) {
        if (job.getImportConfig() == null) {
            throw new BusinessException(
                    "El trabajo de importación no tiene una configuración activa."
            );
        }

        return streamingConfigurableParser;
    }
}