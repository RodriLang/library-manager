package com.rodrilang.librarymanager.inventory.dto.filter;

import com.rodrilang.librarymanager.dto.response.SelectableEntityResponse;

import java.util.List;

public record InventoryFilterOptionsResponse(
        List<SelectableEntityResponse> authors,
        List<SelectableEntityResponse> publishers
) {
}