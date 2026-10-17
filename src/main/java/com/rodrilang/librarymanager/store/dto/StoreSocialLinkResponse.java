package com.rodrilang.librarymanager.store.dto;
import com.rodrilang.librarymanager.store.model.StoreSocialLinkType;
public record StoreSocialLinkResponse(StoreSocialLinkType type, String url, boolean enabled, int displayOrder) {}
