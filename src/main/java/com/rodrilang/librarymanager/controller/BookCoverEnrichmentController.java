package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.metadata.cover.BookCoverEnrichmentService;
import com.rodrilang.librarymanager.metadata.cover.job.CoverEnrichmentJobService;
import com.rodrilang.librarymanager.metadata.cover.job.dto.CoverEnrichmentJobStatusResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Portadas de libros", description = "Administración y enriquecimiento automático de portadas del catálogo")
@RestController
@RequestMapping("/api/admin/books/covers")
@RequiredArgsConstructor
public class BookCoverEnrichmentController {

    private final CoverEnrichmentJobService coverEnrichmentJobService;
    private final BookCoverEnrichmentService bookCoverEnrichmentService;

    @PostMapping("/jobs")
    public CoverEnrichmentJobStatusResponse startJob(
            @RequestParam(defaultValue = "25") int batchSize
    ) {
        return coverEnrichmentJobService.startJob(batchSize);
    }

    @GetMapping("/jobs/{jobId}")
    public CoverEnrichmentJobStatusResponse getJobStatus(@PathVariable Long jobId) {
        return coverEnrichmentJobService.getStatus(jobId);
    }

    @PostMapping("/{bookId}/enrich")
    public Map<String, Boolean> enrichOne(@PathVariable Long bookId) {
        boolean updated = bookCoverEnrichmentService.enrichBookCover(bookId);
        return Map.of("updated", updated);
    }
}