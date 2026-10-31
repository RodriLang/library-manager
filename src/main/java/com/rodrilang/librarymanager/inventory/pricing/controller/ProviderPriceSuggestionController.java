package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderPriceSuggestionResponse;
import com.rodrilang.librarymanager.inventory.pricing.service.ProviderPriceSuggestionService;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/price-suggestions")
@RequiredArgsConstructor
public class ProviderPriceSuggestionController {
    private final ProviderPriceSuggestionService suggestions;
    private final BookRepository books;

    @GetMapping("/books/{bookId}")
    public ProviderPriceSuggestionResponse forBook(@PathVariable Long bookId) {
        return suggestions.findForBook(bookId);
    }

    @PostMapping("/batch")
    public List<ProviderPriceSuggestionResponse> batch(@RequestBody Map<String, List<Long>> payload) {
        return suggestions.findForBooks(payload.getOrDefault("bookIds", List.of()));
    }

    // An ISBN may be in the shared catalog even if not yet held by this bookstore.
    // We never infer an approximate bibliographic match as an exact price reference.
    @GetMapping("/isbn/{isbn}")
    public ProviderPriceSuggestionResponse byIsbn(@PathVariable String isbn) {
        String normalized = isbn == null ? "" : isbn.replaceAll("[^0-9Xx]", "").toUpperCase();
        Long bookId = normalized.length() == 13
                ? books.findByIsbn13(normalized).map(book -> book.getId()).orElse(null)
                : normalized.length() == 10
                    ? books.findByIsbn10(normalized).map(book -> book.getId()).orElse(null)
                    : null;
        return suggestions.findForBook(bookId);
    }
}
