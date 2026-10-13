package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class InventoryPriceImportPreviewWorker {

    private final InventoryPriceImportService importService;
    private final PriceListImportFileStorage fileStorage;

    public void process(Long importId, String sourcePath) {
        Path source = Path.of(sourcePath);
        try {
            importService.processPreview(importId, source);
        } finally {
            fileStorage.deleteQuietly(source);
        }
    }
}
