package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceHealthIssueType;

public record EditorialPriceHealthCountResponse(
        EditorialPriceHealthIssueType type,
        long count
) {
}