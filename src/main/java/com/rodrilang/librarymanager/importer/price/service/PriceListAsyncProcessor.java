package com.rodrilang.librarymanager.importer.price.service;

import java.nio.file.Path;

public interface PriceListAsyncProcessor {

    void process(Long jobId, Path filePath);
}