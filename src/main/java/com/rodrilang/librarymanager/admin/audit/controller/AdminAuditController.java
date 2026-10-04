package com.rodrilang.librarymanager.admin.audit.controller;

import com.rodrilang.librarymanager.admin.audit.dto.AdminAuditLogResponse;
import com.rodrilang.librarymanager.admin.audit.repository.AdminAuditLogRepository;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {
    private final AdminAuditLogRepository repository;
    @GetMapping
    public PageResponse<AdminAuditLogResponse> list(Pageable pageable) {
        return PageResponse.of(repository.findAllByOrderByCreatedAtDesc(pageable).map(a -> new AdminAuditLogResponse(
                a.getId(), a.getActorUserId(), a.getBookstoreId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getSummary(), a.getCreatedAt()
        )));
    }
}
