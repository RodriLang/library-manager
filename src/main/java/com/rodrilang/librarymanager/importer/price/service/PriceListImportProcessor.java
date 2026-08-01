package com.rodrilang.librarymanager.importer.price.service;

public interface PriceListImportProcessor {

    void process(Long jobId, byte[] fileBytes);
}