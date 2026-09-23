package com.rodrilang.librarymanager.admin.invitation.controller;

import com.rodrilang.librarymanager.admin.invitation.dto.AdminInvitationResponse;
import com.rodrilang.librarymanager.admin.invitation.service.AdminInvitationService;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvitationController {

    private final AdminInvitationService service;

    @GetMapping
    public PageResponse<AdminInvitationResponse> list(Pageable pageable) {
        return service.findAll(pageable);
    }

    @PostMapping("/{invitationId}/revoke")
    public void revoke(@PathVariable Long invitationId) {
        service.revoke(invitationId);
    }
}