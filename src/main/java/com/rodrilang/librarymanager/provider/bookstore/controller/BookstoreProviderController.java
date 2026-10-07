package com.rodrilang.librarymanager.provider.bookstore.controller;

import com.rodrilang.librarymanager.provider.bookstore.dto.BookstoreProviderResponse;
import com.rodrilang.librarymanager.provider.bookstore.dto.CreateBookstoreProviderRequest;
import com.rodrilang.librarymanager.provider.bookstore.dto.UpdateBookstoreProviderRequest;
import com.rodrilang.librarymanager.provider.bookstore.service.BookstoreProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookstore/providers")
@RequiredArgsConstructor
public class BookstoreProviderController {

    private final BookstoreProviderService service;

    @GetMapping
    public List<BookstoreProviderResponse> mine() {
        return service.findMine();
    }

    @PostMapping
    public ResponseEntity<BookstoreProviderResponse> create(
            @Valid @RequestBody CreateBookstoreProviderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{providerId}")
    public BookstoreProviderResponse update(
            @PathVariable Long providerId,
            @Valid @RequestBody UpdateBookstoreProviderRequest request
    ) {
        return service.update(providerId, request);
    }

    @PostMapping("/{providerId}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long providerId) {
        service.withdraw(providerId);
    }
}
