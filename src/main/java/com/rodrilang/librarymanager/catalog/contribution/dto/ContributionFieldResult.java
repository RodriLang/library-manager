package com.rodrilang.librarymanager.catalog.contribution.dto;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.ContributionAction;

public record ContributionFieldResult(
        BookField field,
        ContributionAction action,
        Long proposalId
) {
}
