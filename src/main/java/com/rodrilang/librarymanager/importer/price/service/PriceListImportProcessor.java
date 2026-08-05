package com.rodrilang.librarymanager.importer.price.service;

import java.nio.file.Path;

public interface PriceListImportProcessor {

    void process(Long jobId, Path filePath);
}