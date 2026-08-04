package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;

import java.util.List;

public interface PriceListRowDeduplicator {

     List<PriceListRow> deduplicate(List<PriceListRow> rows);

}