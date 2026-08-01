package com.rodrilang.librarymanager.importer.price.service;

public interface PriceListAsyncProcessor {

    void process(Long jobId, byte[] fileBytes);
}