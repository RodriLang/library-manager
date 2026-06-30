package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.AddAuthorsRequest;
import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.service.BookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Libros", description = "Gestión de obras literarias")
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    public ResponseEntity<BookDetailResponse> create(@RequestBody BookRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.create(request));
    }

    @PostMapping("/{bookId}/authors")
    public ResponseEntity<BookDetailResponse> addAuthors(
            @PathVariable Long bookId,
            @RequestBody AddAuthorsRequest request
    ) {
        return ResponseEntity.ok(
                bookService.addAuthors(bookId, request)
        );
    }

    @GetMapping
    public ResponseEntity<PageResponse<BookSummaryResponse>> getAll(
            @ParameterObject
            @PageableDefault(size = 20, sort = "name")Pageable pageable){

        return ResponseEntity.ok(PageResponse.of(bookService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookDetailResponse> getById(@PathVariable Long id){
        return ResponseEntity.ok(bookService.getById(id));
    }

    @GetMapping("/isbn/{ISBN}")
    public ResponseEntity<BookDetailResponse> getByIsbn(@PathVariable("ISBN") String isbn){
        return ResponseEntity.ok(bookService.getByIsbn(isbn));
    }
}
