package com.rodrilang.librarymanager.cover.controller;

import com.rodrilang.librarymanager.cover.dto.BookCoverResponse;
import com.rodrilang.librarymanager.cover.service.BookCoverDeletionService;
import com.rodrilang.librarymanager.cover.service.BookCoverSelectionService;
import com.rodrilang.librarymanager.cover.service.BookCoverService;
import com.rodrilang.librarymanager.cover.service.BookCoverUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/covers")
@RequiredArgsConstructor
@Tag(
        name = "Book Covers",
        description = "Administración de portadas de libros"
)
public class BookCoverController {

    private final BookCoverService bookCoverService;
    private final BookCoverUploadService bookCoverUploadService;
    private final BookCoverSelectionService bookCoverSelectionService;
    private final BookCoverDeletionService bookCoverDeletionService;

    @Operation(
            summary = "Subir una portada",
            description = """
                Sube una portada manual para un libro.
                La imagen se guarda en Cloudinary y se establece
                como portada principal.
                """
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookCoverResponse> upload(
            @PathVariable Long bookId,
            @Parameter(
                    description = "Archivo JPEG, PNG o WEBP",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(
                                    type = "string",
                                    format = "binary"
                            )
                    )
            )
            @RequestPart("file") MultipartFile file
    ) {
        BookCoverResponse response =
                bookCoverUploadService.uploadManualCover(
                        bookId,
                        file
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Listar portadas",
            description = "Obtiene todas las portadas disponibles asociadas a un libro."
    )
    @GetMapping
    public ResponseEntity<List<BookCoverResponse>> findAll(
            @PathVariable Long bookId
    ) {
        return ResponseEntity.ok(
                bookCoverService.findAvailableByBookId(bookId)
        );
    }

    @Operation(
            summary = "Obtener portada principal",
            description = "Obtiene la portada principal del libro."
    )
    @GetMapping("/primary")
    public ResponseEntity<BookCoverResponse> findPrimary(
            @PathVariable Long bookId
    ) {
        return ResponseEntity.ok(
                bookCoverService.getPrimaryByBookId(bookId)
        );
    }

    @Operation(
            summary = "Seleccionar portada principal",
            description = "Establece una portada existente como portada principal del libro."
    )
    @PutMapping("/{coverId}/primary")
    public ResponseEntity<BookCoverResponse> selectPrimary(
            @PathVariable Long bookId,
            @PathVariable Long coverId
    ) {
        return ResponseEntity.ok(
                bookCoverSelectionService.selectPrimary(
                        bookId,
                        coverId
                )
        );
    }

    @Operation(
            summary = "Eliminar portada",
            description = "Elimina una portada del libro. Si era la principal, selecciona automáticamente otra disponible."
    )
    @DeleteMapping("/{coverId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long bookId,
            @PathVariable Long coverId
    ) {
        bookCoverDeletionService.delete(bookId, coverId);

        return ResponseEntity.noContent().build();
    }
}