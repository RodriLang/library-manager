package com.rodrilang.librarymanager.editorialprice.service;

import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthIssueResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthSummaryResponse;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConflictScope;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceHealthIssueType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EditorialPriceHealthService {

    EditorialPriceHealthSummaryResponse getSummary();

    Page<EditorialPriceHealthIssueResponse> findIssues(
            EditorialPriceHealthIssueType type,
            EditorialPriceConflictScope conflictScope,
            String query,
            Pageable pageable
    );

}