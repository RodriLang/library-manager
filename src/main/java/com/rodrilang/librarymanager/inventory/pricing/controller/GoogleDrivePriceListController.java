package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.inventory.pricing.service.GoogleDrivePriceListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/inventory/price-imports/drive")
@RequiredArgsConstructor
public class GoogleDrivePriceListController {
    private final GoogleDrivePriceListService service;
    public record ExploreRequest(String url) {}
    public record DownloadRequest(String url, String fileId) {}

    @PostMapping("/explore")
    public GoogleDrivePriceListService.FolderFiles explore(@RequestBody ExploreRequest request) {
        return service.explore(request.url());
    }

    @PostMapping("/stream")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> stream(
            @RequestBody DownloadRequest request) {
        // Validate Google Drive's HTTP status before committing a binary response.
        var source = service.openDownload(request.url(), request.fileId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(output -> service.streamDownload(source, output));
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestBody DownloadRequest request) {
        var file = service.download(request.url(), request.fileId());
        String safe = file.filename().replace("\r", "_").replace("\n", "_").replace("\"", "_");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(safe, StandardCharsets.UTF_8))
                .body(file.content());
    }
}
