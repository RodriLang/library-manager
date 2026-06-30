package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.AuthorRequest;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.service.AuthorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "Autores", description = "Gestión de los dueños de la propiedad intelectual de los libros")
@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    @PostMapping
    public ResponseEntity<AuthorResponse> create(
            @RequestBody AuthorRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authorService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorResponse> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(authorService.findById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuthorResponse>> findAll(
            @ParameterObject
            @PageableDefault(size = 20, sort = "name")
            Pageable pageable) {

        return ResponseEntity.ok(PageResponse.of(authorService.findAll(pageable)));
    }

    @GetMapping("/search")
    public ResponseEntity<List<AuthorResponse>> search(
            @RequestParam String q) {

        return ResponseEntity.ok(authorService.search(q));
    }
}