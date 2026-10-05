package com.rodrilang.librarymanager.catalog.contribution.dto;

import com.rodrilang.librarymanager.catalog.contribution.enums.ProposalReviewDecision;
import jakarta.validation.constraints.NotNull;

public record ReviewBookFieldProposalRequest(
        @NotNull ProposalReviewDecision decision
) {
}
