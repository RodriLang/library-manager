package com.rodrilang.librarymanager.editorialprice.controller;

import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthIssueResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthSummaryResponse;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConflictScope;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceHealthIssueType;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/editorial-prices/health")
@RequiredArgsConstructor
public class EditorialPriceHealthController {

    private final EditorialPriceHealthService editorialPriceHealthService;

    @GetMapping("/summary")
    public EditorialPriceHealthSummaryResponse getSummary() {
        return editorialPriceHealthService.getSummary();
    }

    @GetMapping("/issues")
    public Page<EditorialPriceHealthIssueResponse> findIssues(
            @RequestParam EditorialPriceHealthIssueType type,
            @RequestParam(required = false) EditorialPriceConflictScope scope,
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return editorialPriceHealthService.findIssues(type, scope, query, pageable);
    }
}