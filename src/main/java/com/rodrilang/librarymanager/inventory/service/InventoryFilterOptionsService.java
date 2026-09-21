package com.rodrilang.librarymanager.inventory.service;

import com.rodrilang.librarymanager.inventory.dto.filter.InventoryFilterOptionsRequest;
import com.rodrilang.librarymanager.inventory.dto.filter.InventoryFilterOptionsResponse;
import com.rodrilang.librarymanager.dto.response.SelectableEntityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryFilterOptionsService {

    Page<SelectableEntityResponse> searchAuthors(
            String query,
            Pageable pageable
    );

    Page<SelectableEntityResponse> searchPublishers(
            String query,
            Pageable pageable
    );

    InventoryFilterOptionsResponse resolve(
            InventoryFilterOptionsRequest request
    );
}