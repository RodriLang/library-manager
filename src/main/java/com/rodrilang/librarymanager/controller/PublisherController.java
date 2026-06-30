package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.PublisherRequest;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.service.PublisherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Editoriales", description = "Gestión de empresas que comercializan las obras")
@RestController
@RequestMapping("/api/publishers")
@RequiredArgsConstructor
public class PublisherController {

    private final PublisherService publisherService;

    @PostMapping
    public ResponseEntity<PublisherResponse> create(
            @Valid @RequestBody PublisherRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(publisherService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublisherResponse> findById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(publisherService.findById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PublisherResponse>> findAll(
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(publisherService.findAll(pageable))
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<PublisherResponse>> search(
            @RequestParam String q
    ) {
        return ResponseEntity.ok(
                publisherService.search(q)
        );
    }
}