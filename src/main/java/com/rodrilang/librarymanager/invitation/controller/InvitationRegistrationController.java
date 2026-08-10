package com.rodrilang.librarymanager.invitation.controller;

import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;
import com.rodrilang.librarymanager.invitation.dto.InvitationRegisterRequest;
import com.rodrilang.librarymanager.invitation.dto.InvitationValidationResponse;
import com.rodrilang.librarymanager.invitation.services.BookstoreInvitationService;
import com.rodrilang.librarymanager.invitation.services.InvitationRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/invitations")
@RequiredArgsConstructor
public class InvitationRegistrationController {

    private final BookstoreInvitationService invitationService;
    private final InvitationRegistrationService registrationService;

    @GetMapping("/{token}")
    public InvitationValidationResponse validate(
            @PathVariable String token) {

        return invitationService.validate(token);
    }

    @PostMapping("/{token}/register")
    public AuthResponse register(
            @PathVariable String token,
            @Valid @RequestBody InvitationRegisterRequest request) {

        return registrationService.register(token, request);
    }
}
