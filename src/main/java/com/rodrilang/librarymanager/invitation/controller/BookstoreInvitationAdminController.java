package com.rodrilang.librarymanager.invitation.controller;

import com.rodrilang.librarymanager.invitation.dto.CreateBookstoreInvitationRequest;
import com.rodrilang.librarymanager.invitation.dto.CreateBookstoreInvitationResponse;
import com.rodrilang.librarymanager.invitation.services.BookstoreInvitationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookstore-invitations")
@RequiredArgsConstructor
@Tag(
        name = "Bookstore Invitations",
        description = "Administración de invitaciones para registrar usuarios en una librería"
)
public class BookstoreInvitationAdminController {

    private final BookstoreInvitationService service;

    @PostMapping
    public CreateBookstoreInvitationResponse create(
            @Valid
            @RequestBody CreateBookstoreInvitationRequest request) {

        return service.create(request);
    }
}
