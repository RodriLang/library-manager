package com.rodrilang.librarymanager.provider.bookstore.controller;

import com.rodrilang.librarymanager.provider.bookstore.dto.*;
import com.rodrilang.librarymanager.provider.bookstore.service.BookstoreProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookstore/providers")
@RequiredArgsConstructor
public class BookstoreProviderController {
    private final BookstoreProviderService service;
    @GetMapping public List<BookstoreProviderResponse> mine(){ return service.findMine(); }
    @PutMapping("/{providerId}") public BookstoreProviderResponse update(@PathVariable Long providerId,@Valid @RequestBody UpdateBookstoreProviderRequest request){ return service.update(providerId,request); }
}
