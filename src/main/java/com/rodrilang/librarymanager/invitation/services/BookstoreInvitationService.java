package com.rodrilang.librarymanager.invitation.services;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.invitation.dto.CreateBookstoreInvitationRequest;
import com.rodrilang.librarymanager.invitation.dto.CreateBookstoreInvitationResponse;
import com.rodrilang.librarymanager.invitation.dto.InvitationValidationResponse;
import com.rodrilang.librarymanager.invitation.event.BookstoreInvitationCreatedEvent;
import com.rodrilang.librarymanager.invitation.model.BookstoreInvitation;
import com.rodrilang.librarymanager.invitation.model.InvitationStatus;
import com.rodrilang.librarymanager.invitation.repository.BookstoreInvitationRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BookstoreInvitationService {

    private static final int DEFAULT_EXPIRATION_HOURS = 48;

    private final BookstoreInvitationRepository invitationRepository;
    private final InvitationTokenGenerator tokenGenerator;
    private final BookstoreService bookstoreService;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CreateBookstoreInvitationResponse create(CreateBookstoreInvitationRequest request) {

        Bookstore bookstore = bookstoreService.getEntityById(request.bookstoreId());

        if (!Boolean.TRUE.equals(bookstore.getActive())) {
            throw new BusinessException("No se pueden invitar usuarios a una librería inactiva.");
        }

        String token = tokenGenerator.generate();
        String tokenHash = tokenGenerator.hash(token);

        int expirationHours =
                request.expirationHours() != null
                        ? request.expirationHours()
                        : DEFAULT_EXPIRATION_HOURS;

        BookstoreInvitation invitation =
                BookstoreInvitation.builder()
                        .bookstore(bookstore)
                        .email(normalizeEmail(request.email()))
                        .role(request.role())
                        .tokenHash(tokenHash)
                        .expiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS))
                        .build();

        BookstoreInvitation saved = invitationRepository.save(invitation);


        if (saved.getEmail() != null) {

            eventPublisher.publishEvent(
                    new BookstoreInvitationCreatedEvent(
                            saved.getId(),
                            saved.getEmail(),
                            bookstore.getName(),
                            token,
                            saved.getExpiresAt()
                    )
            );
        }

        return new CreateBookstoreInvitationResponse(
                saved.getId(),
                bookstore.getId(),
                bookstore.getName(),
                saved.getEmail(),
                saved.getRole(),
                token,
                saved.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public InvitationValidationResponse validate(String token) {

        String tokenHash = tokenGenerator.hash(token);

        return invitationRepository
                .findByTokenHash(tokenHash)
                .map(this::toValidationResponse)
                .orElseGet(this::invalidResponse);
    }

    @Transactional
    public BookstoreInvitation getValidInvitationForRegistration(
            String token) {

        String tokenHash = tokenGenerator.hash(token);

        BookstoreInvitation invitation = invitationRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException("La invitación no es válida"));

        validateInvitation(invitation);

        return invitation;
    }

    private InvitationValidationResponse toValidationResponse(
            BookstoreInvitation invitation) {

        InvitationStatus status = resolveStatus(invitation);

        return new InvitationValidationResponse(
                status,
                invitation.getBookstore().getId(),
                invitation.getBookstore().getName(),
                invitation.getEmail(),
                invitation.getExpiresAt()
        );
    }

    private InvitationValidationResponse invalidResponse() {
        return new InvitationValidationResponse(
                InvitationStatus.INVALID,
                null,
                null,
                null,
                null
        );
    }

    private InvitationStatus resolveStatus(
            BookstoreInvitation invitation) {

        if (invitation.isRevoked()) {
            return InvitationStatus.REVOKED;
        }

        if (invitation.isUsed()) {
            return InvitationStatus.USED;
        }

        if (invitation.isExpired()) {
            return InvitationStatus.EXPIRED;
        }

        return InvitationStatus.VALID;
    }

    private void validateInvitation(
            BookstoreInvitation invitation) {

        if (invitation.isRevoked()) {
            throw new BusinessException("La invitación fue revocada");
        }

        if (invitation.isUsed()) {
            throw new BusinessException("La invitación ya fue utilizada");
        }

        if (invitation.isExpired()) {
            throw new BusinessException("La invitación ha vencido");
        }
    }

    private String normalizeEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }
}