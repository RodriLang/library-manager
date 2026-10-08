package com.rodrilang.librarymanager.store.dto;
import jakarta.validation.constraints.Size;
public record UpdateStorePublicationRequest(
        Boolean published,
        Boolean featured,
        Integer featuredOrder,
        @Size(max=500) String customTitle,
        String customDescription,
        @Size(max=255) String seoTitle,
        @Size(max=500) String seoDescription
) {}
