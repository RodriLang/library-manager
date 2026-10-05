package com.rodrilang.librarymanager.purchasing.order.export;

public record PurchaseOrderExportFile(
        String fileName,
        String contentType,
        byte[] content
) {
}
