package com.rodrilang.librarymanager.admin.quality.dto;

import java.util.List;

public record CatalogQualityResponse(long totalBooks, List<CatalogIssueResponse> issues) {
    public record CatalogIssueResponse(String code, String label, long count) {}
}
