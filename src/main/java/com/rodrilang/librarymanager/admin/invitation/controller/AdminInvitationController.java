package com.rodrilang.librarymanager.admin.invitation.controller;

import com.rodrilang.librarymanager.admin.invitation.dto.AdminInvitationResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.invitation.model.BookstoreInvitation;
import com.rodrilang.librarymanager.invitation.repository.BookstoreInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvitationController {
    private final BookstoreInvitationRepository repository;

    @GetMapping
    public PageResponse<AdminInvitationResponse> list(Pageable pageable) {
        return PageResponse.of(repository.findAll(pageable).map(this::toResponse));
    }

    @PostMapping("/{invitationId}/revoke")
    public void revoke(@PathVariable Long invitationId) {
        BookstoreInvitation invitation = repository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la invitación."));
        if (invitation.getRevokedAt() == null && invitation.getUsedAt() == null) {
            invitation.setRevokedAt(Instant.now());
            repository.save(invitation);
        }
    }

    private AdminInvitationResponse toResponse(BookstoreInvitation i) {
        String status = i.isRevoked() ? "REVOKED" : i.isUsed() ? "ACCEPTED" : i.isExpired() ? "EXPIRED" : "PENDING";
        return new AdminInvitationResponse(i.getId(), i.getBookstore().getId(), i.getBookstore().getName(), i.getEmail(), i.getRole(), status, i.getExpiresAt(), i.getCreatedAt());
    }
}
