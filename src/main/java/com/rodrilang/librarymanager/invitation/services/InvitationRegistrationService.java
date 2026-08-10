package com.rodrilang.librarymanager.invitation.services;

import com.rodrilang.librarymanager.auth.dtos.request.LoginRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;
import com.rodrilang.librarymanager.auth.services.AuthService;
import com.rodrilang.librarymanager.auth.services.UserService;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.invitation.dto.InvitationRegisterRequest;
import com.rodrilang.librarymanager.invitation.model.BookstoreInvitation;
import com.rodrilang.librarymanager.invitation.repository.BookstoreInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InvitationRegistrationService {

    private final BookstoreInvitationRepository invitationRepository;
    private final BookstoreInvitationService invitationService;
    private final UserService userService;
    private final AuthService authService;

    @Transactional
    public AuthResponse register(
            String token,
            InvitationRegisterRequest request) {

        BookstoreInvitation invitation =
                invitationService.getValidInvitationForRegistration(token);

        validateEmail(invitation, request.email());

        userService.createUserFromInvitation(
                request,
                invitation.getBookstore(),
                invitation.getRole()
        );

        invitation.setUsedAt(Instant.now());

        invitationRepository.save(invitation);

        return authService.login(
                new LoginRequestDto(
                        request.username(),
                        request.password()
                )
        );
    }

    private void validateEmail(
            BookstoreInvitation invitation,
            String registrationEmail) {

        if (invitation.getEmail() == null) {
            return;
        }

        if (!invitation.getEmail()
                .equalsIgnoreCase(registrationEmail.trim())) {

            throw new BusinessException(
                    "Esta invitación fue enviada a otro correo electrónico"
            );
        }
    }
}