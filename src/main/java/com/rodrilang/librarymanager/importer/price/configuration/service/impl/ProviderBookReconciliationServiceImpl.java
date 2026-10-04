package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookReconciliationPreview;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookReconciliationResult;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookReconciliationService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.provider.catalog.model.ProviderBook;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProviderBookReconciliationServiceImpl implements ProviderBookReconciliationService {

    private final ProviderBookRepository providerBookRepository;
    private final InventoryRepository inventoryRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public ProviderBookReconciliationPreview preview(Long providerBookId, Long targetBookId) {
        ProviderBook providerBook = providerBookRepository.findById(providerBookId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la relación con el proveedor."));

        Book currentBook = providerBook.getBook();
        Book targetBook = bookRepository.findByIdWithDetails(targetBookId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el libro de destino."));

        validateDifferentBooks(currentBook, targetBook);

        Long providerId = providerBook.getProvider().getId();
        ProviderBook existingTargetLink = providerBookRepository
                .findByProviderIdAndBookId(providerId, targetBookId)
                .orElse(null);

        List<String> warnings = buildWarnings(currentBook, targetBook, existingTargetLink);

        return new ProviderBookReconciliationPreview(
                toProviderBookSummary(providerBook),
                toBookSummary(currentBook),
                toBookSummary(targetBook),
                0,
                existingTargetLink != null,
                existingTargetLink != null ? existingTargetLink.getId() : null,
                List.of(),
                true,
                warnings
        );
    }

    @Override
    @Transactional
    public ProviderBookReconciliationResult confirm(Long providerBookId, Long targetBookId) {
        ProviderBook sourceLink = providerBookRepository.findByIdForUpdate(providerBookId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la relación con el proveedor."));

        Book previousBook = sourceLink.getBook();
        Book targetBook = bookRepository.findByIdWithDetails(targetBookId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el libro de destino."));

        validateDifferentBooks(previousBook, targetBook);

        Long providerId = sourceLink.getProvider().getId();
        ProviderBook targetLink = providerBookRepository
                .findByProviderIdAndBookIdForUpdate(providerId, targetBookId)
                .orElse(null);

        boolean providerBookMerged;
        ProviderBook resultingLink;

        if (targetLink == null) {
            sourceLink.setBook(targetBook);
            sourceLink.setIdentifierStatus(ProviderBookIdentifierStatus.MANUALLY_CONFIRMED);
            sourceLink.setActive(true);
            sourceLink.setUpdatedAt(Instant.now());
            resultingLink = providerBookRepository.save(sourceLink);
            providerBookMerged = false;
        } else {
            mergeProviderBookData(sourceLink, targetLink);
            resultingLink = providerBookRepository.save(targetLink);
            providerBookRepository.delete(sourceLink);
            providerBookMerged = true;
        }

        boolean previousBookDeleted = deletePreviousBookIfOrphan(previousBook);

        return new ProviderBookReconciliationResult(
                resultingLink.getId(),
                previousBook.getId(),
                targetBook.getId(),
                0,
                0,
                providerBookMerged,
                previousBookDeleted
        );
    }

    private void mergeProviderBookData(ProviderBook source, ProviderBook target) {
        if (hasText(source.getExternalCode())) {
            target.setExternalCode(source.getExternalCode());
        }
        if (hasText(source.getReportedIsbn())) {
            target.setReportedIsbn(source.getReportedIsbn());
        }
        target.setIdentifierStatus(ProviderBookIdentifierStatus.MANUALLY_CONFIRMED);
        target.setActive(true);
        if (source.getLastSeenAt() != null
                && (target.getLastSeenAt() == null || source.getLastSeenAt().isAfter(target.getLastSeenAt()))) {
            target.setLastSeenAt(source.getLastSeenAt());
        }
        target.setUpdatedAt(Instant.now());
    }

    private boolean deletePreviousBookIfOrphan(Book book) {
        boolean hasInventory = inventoryRepository.existsByBookId(book.getId());
        boolean hasProviderLinks = providerBookRepository.existsByBookId(book.getId());

        if (hasInventory || hasProviderLinks) {
            return false;
        }

        // La conciliación de proveedores ya no elimina libros automáticamente.
        // El origen global por listas editoriales dejó de existir y el catálogo compartido
        // conserva sus registros aunque una relación comercial sea removida.
        return false;
    }

    private void validateDifferentBooks(Book currentBook, Book targetBook) {
        if (Objects.equals(currentBook.getId(), targetBook.getId())) {
            throw new BusinessException("El libro de origen y el libro de destino son el mismo.");
        }
    }

    private List<String> buildWarnings(Book currentBook, Book targetBook, ProviderBook existingTargetLink) {
        List<String> warnings = new ArrayList<>();

        if (!normalize(currentBook.getTitle()).equals(normalize(targetBook.getTitle()))) {
            warnings.add("Los títulos de los libros son diferentes.");
        }
        if (hasText(currentBook.getIsbn13())
                && hasText(targetBook.getIsbn13())
                && !currentBook.getIsbn13().equals(targetBook.getIsbn13())) {
            warnings.add("Ambos libros tienen ISBN-13 diferentes.");
        }
        if (existingTargetLink != null) {
            warnings.add("El libro de destino ya está relacionado con este proveedor. Las relaciones se fusionarán.");
        }
        if (inventoryRepository.existsByBookId(currentBook.getId())) {
            warnings.add("El libro de origen tiene inventario. No será eliminado después de la conciliación.");
        }
        return warnings;
    }

    private ProviderBookReconciliationPreview.ProviderBookSummary toProviderBookSummary(ProviderBook providerBook) {
        return new ProviderBookReconciliationPreview.ProviderBookSummary(
                providerBook.getId(),
                providerBook.getProvider().getId(),
                providerBook.getProvider().getName(),
                providerBook.getExternalCode(),
                providerBook.getReportedIsbn(),
                providerBook.getIdentifierStatus() != null ? providerBook.getIdentifierStatus().name() : null
        );
    }

    private ProviderBookReconciliationPreview.BookSummary toBookSummary(Book book) {
        return new ProviderBookReconciliationPreview.BookSummary(
                book.getId(),
                book.getTitle(),
                book.getIsbn10(),
                book.getIsbn13(),
                book.getPublisher() != null ? book.getPublisher().getName() : null,
                inventoryRepository.existsByBookId(book.getId())
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
