package com.rodrilang.librarymanager.store.dto;
import com.rodrilang.librarymanager.store.model.StoreSectionType;
import java.util.List;
public record StorefrontSectionResponse(Long id, StoreSectionType type, String slug, String title, List<StorefrontProductResponse> products) {}
