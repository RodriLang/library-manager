package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.dto.response.PublisherConfigurationDetailResponse;
import com.rodrilang.librarymanager.dto.response.PublisherConfigurationResponse;
import com.rodrilang.librarymanager.enums.PublisherCatalogSort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookstoreCatalogSettingsService {

    Page<PublisherConfigurationResponse> searchPublishers(
            String query,
            Boolean excluded,
            PublisherCatalogSort sort,
            Pageable pageable
    );

    PublisherConfigurationDetailResponse getPublisher(Long publisherId);

    Page<BookSummaryResponse> getPublisherBooks(
            Long publisherId,
            String query,
            Pageable pageable
    );

    void updatePublisherExclusion(Long publisherId, boolean excluded);
}