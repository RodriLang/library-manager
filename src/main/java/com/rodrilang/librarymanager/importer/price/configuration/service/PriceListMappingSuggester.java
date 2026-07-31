package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListSuggestedMappingResponse;

import java.util.List;

public interface PriceListMappingSuggester {

    List<PriceListSuggestedMappingResponse> suggest(PriceListPreviewRowResponse headerRow);
}