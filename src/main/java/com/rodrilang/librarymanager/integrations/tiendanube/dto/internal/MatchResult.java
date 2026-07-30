package com.rodrilang.librarymanager.integrations.tiendanube.dto.internal;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.InventoryMatchCandidateResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;

import java.util.List;

public record MatchResult(
        TiendanubeMatchType type,
        List<InventoryMatchCandidateResponse> candidates
) {
}