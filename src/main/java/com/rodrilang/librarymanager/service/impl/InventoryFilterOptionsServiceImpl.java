package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.internal.InventoryFilterOption;
import com.rodrilang.librarymanager.dto.response.SelectableEntityResponse;
import com.rodrilang.librarymanager.inventory.dto.filter.InventoryFilterOptionsRequest;
import com.rodrilang.librarymanager.inventory.dto.filter.InventoryFilterOptionsResponse;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.InventoryFilterOptionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryFilterOptionsServiceImpl
        implements InventoryFilterOptionsService {

    private final InventoryRepository inventoryRepository;
    private final BookstoreContext bookstoreContext;

    @Override
    public Page<SelectableEntityResponse> searchAuthors(
            String query,
            Pageable pageable
    ) {
        Long bookstoreId =
                requireBookstoreId();

        return inventoryRepository
                .searchFilterAuthors(
                        bookstoreId,
                        query,
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    public Page<SelectableEntityResponse> searchPublishers(
            String query,
            Pageable pageable
    ) {
        Long bookstoreId =
                requireBookstoreId();

        return inventoryRepository
                .searchFilterPublishers(
                        bookstoreId,
                        query,
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    public InventoryFilterOptionsResponse resolve(
            InventoryFilterOptionsRequest request
    ) {
        Long bookstoreId =
                requireBookstoreId();

        List<Long> authorIds =
                normalizeIds(
                        request != null
                                ? request.authorIds()
                                : null
                );

        List<Long> publisherIds =
                normalizeIds(
                        request != null
                                ? request.publisherIds()
                                : null
                );

        List<SelectableEntityResponse> authors =
                inventoryRepository
                        .findFilterAuthorsByIds(
                                bookstoreId,
                                authorIds
                        )
                        .stream()
                        .map(this::toResponse)
                        .toList();

        List<SelectableEntityResponse> publishers =
                inventoryRepository
                        .findFilterPublishersByIds(
                                bookstoreId,
                                publisherIds
                        )
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return new InventoryFilterOptionsResponse(
                authors,
                publishers
        );
    }

    private SelectableEntityResponse toResponse(
            InventoryFilterOption option
    ) {
        return new SelectableEntityResponse(
                option.id(),
                option.name()
        );
    }

    private List<Long> normalizeIds(
            List<Long> ids
    ) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .filter(
                        id ->
                                id != null
                                        && id > 0
                )
                .distinct()
                .toList();
    }

    private Long requireBookstoreId() {
        return bookstoreContext
                .getCurrentBookstoreId();
    }
}