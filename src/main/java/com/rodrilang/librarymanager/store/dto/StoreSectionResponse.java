package com.rodrilang.librarymanager.store.dto;
import com.rodrilang.librarymanager.store.model.StoreSectionType;
public record StoreSectionResponse(Long id, StoreSectionType type, String name, String slug, boolean enabled, int displayOrder, Integer windowDays, int maxItems, long itemCount) {}
