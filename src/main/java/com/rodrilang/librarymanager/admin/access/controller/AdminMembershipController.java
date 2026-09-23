package com.rodrilang.librarymanager.admin.access.controller;

import com.rodrilang.librarymanager.admin.access.dto.AssignMembershipRequest;
import com.rodrilang.librarymanager.auth.access.dto.BookstoreMembershipResponse;
import com.rodrilang.librarymanager.auth.access.service.BookstoreMembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users/{userId}/memberships")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMembershipController {
    private final BookstoreMembershipService service;

    @GetMapping public List<BookstoreMembershipResponse> list(@PathVariable Long userId) { return service.findForUser(userId); }
    @PostMapping public BookstoreMembershipResponse assign(@PathVariable Long userId, @Valid @RequestBody AssignMembershipRequest request) {
        return service.assign(userId, request.bookstoreId(), request.role());
    }
    @PatchMapping("/{bookstoreId}/enabled") public BookstoreMembershipResponse enabled(@PathVariable Long userId, @PathVariable Long bookstoreId, @RequestParam boolean value) {
        return service.setEnabled(userId, bookstoreId, value);
    }
    @DeleteMapping("/{bookstoreId}") public ResponseEntity<Void> remove(@PathVariable Long userId, @PathVariable Long bookstoreId) {
        service.remove(userId, bookstoreId); return ResponseEntity.noContent().build();
    }
}
