package com.rodrilang.librarymanager.admin.invitation.service;

import com.rodrilang.librarymanager.admin.invitation.dto.AdminInvitationResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.invitation.model.BookstoreInvitation;
import com.rodrilang.librarymanager.invitation.repository.BookstoreInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminInvitationService {

    private final BookstoreInvitationRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<AdminInvitationResponse> findAll(Pageable pageable) {
        return PageResponse.of(
                repository.findAllForAdmin(pageable)
                        .map(this::toResponse)
        );
    }

    @Transactional
    public void revoke(Long invitationId) {
        BookstoreInvitation invitation = repository.findByIdForUpdate(invitationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró la invitación."
                        )
                );

        if (invitation.isUsed() || invitation.isRevoked()) {
            return;
        }

        invitation.setRevokedAt(Instant.now());
    }

    private AdminInvitationResponse toResponse(BookstoreInvitation invitation) {
        String status = resolveStatus(invitation);

        return new AdminInvitationResponse(
                invitation.getId(),
                invitation.getBookstore().getId(),
                invitation.getBookstore().getName(),
                invitation.getEmail(),
                invitation.getRole(),
                status,
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
    }

    private String resolveStatus(BookstoreInvitation invitation) {
        if (invitation.isRevoked()) {
            return "REVOKED";
        }

        if (invitation.isUsed()) {
            return "ACCEPTED";
        }

        if (invitation.isExpired()) {
            return "EXPIRED";
        }

        return "PENDING";
    }
}