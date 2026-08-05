package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface StreamingConfigurablePriceListParser {

    void parse(
            Path filePath,
            PriceListImportConfig config,
            Consumer<PriceListRow> rowConsumer
    );
}