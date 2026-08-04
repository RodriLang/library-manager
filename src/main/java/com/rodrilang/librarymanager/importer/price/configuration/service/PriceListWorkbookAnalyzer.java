package com.rodrilang.librarymanager.importer.price.configuration.service;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import org.springframework.web.multipart.MultipartFile;

public interface PriceListWorkbookAnalyzer {

    PriceListWorkbookAnalysisResponse analyze(MultipartFile file);
}