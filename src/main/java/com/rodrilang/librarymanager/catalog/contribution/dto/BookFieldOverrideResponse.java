package com.rodrilang.librarymanager.catalog.contribution.dto;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldSource;

import java.time.Instant;

public record BookFieldOverrideResponse(
        BookField field,
        String localValue,
        String localDisplayValue,
        String catalogValue,
        String catalogDisplayValue,
        BookFieldSource catalogSource,
        Long proposalId,
        BookFieldProposalStatus proposalStatus,
        Instant updatedAt
) {
}
