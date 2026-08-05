package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookReconciliationPreview;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookReconciliationResult;
import com.rodrilang.librarymanager.importer.price.configuration.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookReconciliationService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProviderBookReconciliationServiceImpl
        implements ProviderBookReconciliationService {

    private final ProviderBookRepository providerBookRepository;
    private final EditorialPriceRepository editorialPriceRepository;
    private final InventoryRepository inventoryRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public ProviderBookReconciliationPreview preview(
            Long providerBookId,
            Long targetBookId
    ) {
        ProviderBook providerBook = providerBookRepository.findById(providerBookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la relación con el proveedor."
                ));

        Book currentBook = providerBook.getBook();

        Book targetBook = bookRepository.findByIdWithDetails(targetBookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el libro de destino."
                ));

        validateDifferentBooks(currentBook, targetBook);

        Long providerId = providerBook.getProvider().getId();

        ProviderBook existingTargetLink = providerBookRepository
                .findByProviderIdAndBookId(providerId, targetBookId)
                .orElse(null);

        List<EditorialPrice> prices = editorialPriceRepository
                .findByProviderIdAndBookIdIn(
                        providerId,
                        List.of(currentBook.getId(), targetBookId)
                );

        PriceAnalysis analysis = analyzePrices(
                prices,
                currentBook.getId(),
                targetBookId
        );

        List<String> warnings = buildWarnings(
                currentBook,
                targetBook,
                existingTargetLink,
                analysis.conflicts()
        );

        return new ProviderBookReconciliationPreview(
                toProviderBookSummary(providerBook),
                toBookSummary(currentBook),
                toBookSummary(targetBook),
                analysis.sourcePrices().size(),
                existingTargetLink != null,
                existingTargetLink != null
                        ? existingTargetLink.getId()
                        : null,
                analysis.conflicts(),
                analysis.conflicts().isEmpty(),
                warnings
        );
    }

    @Override
    @Transactional
    public ProviderBookReconciliationResult confirm(
            Long providerBookId,
            Long targetBookId
    ) {
        ProviderBook sourceLink = providerBookRepository
                .findByIdForUpdate(providerBookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la relación con el proveedor."
                ));

        Book previousBook = sourceLink.getBook();

        Book targetBook = bookRepository.findByIdWithDetails(targetBookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el libro de destino."
                ));

        validateDifferentBooks(previousBook, targetBook);

        Long providerId = sourceLink.getProvider().getId();

        ProviderBook targetLink = providerBookRepository
                .findByProviderIdAndBookIdForUpdate(
                        providerId,
                        targetBookId
                )
                .orElse(null);

        List<EditorialPrice> prices = editorialPriceRepository
                .findByProviderIdAndBookIdInForUpdate(
                        providerId,
                        List.of(previousBook.getId(), targetBookId)
                );

        PriceAnalysis analysis = analyzePrices(
                prices,
                previousBook.getId(),
                targetBookId
        );

        if (!analysis.conflicts().isEmpty()) {
            throw new BusinessException(
                    "No se puede confirmar la conciliación porque existen "
                            + "precios diferentes para las mismas fechas."
            );
        }

        PriceMoveResult priceMoveResult = movePrices(
                analysis,
                targetBook
        );

        boolean providerBookMerged;

        ProviderBook resultingLink;

        if (targetLink == null) {
            sourceLink.setBook(targetBook);
            sourceLink.setIdentifierStatus(
                    ProviderBookIdentifierStatus.MANUALLY_CONFIRMED
            );
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

        boolean previousBookDeleted = deletePreviousBookIfOrphan(
                previousBook
        );

        return new ProviderBookReconciliationResult(
                resultingLink.getId(),
                previousBook.getId(),
                targetBook.getId(),
                priceMoveResult.moved(),
                priceMoveResult.removedDuplicates(),
                providerBookMerged,
                previousBookDeleted
        );
    }

    private PriceAnalysis analyzePrices(
            List<EditorialPrice> prices,
            Long sourceBookId,
            Long targetBookId
    ) {
        List<EditorialPrice> sourcePrices = prices.stream()
                .filter(price -> Objects.equals(
                        price.getBook().getId(),
                        sourceBookId
                ))
                .toList();

        Map<PriceKey, EditorialPrice> targetPricesByKey =
                new HashMap<>();

        prices.stream()
                .filter(price -> Objects.equals(
                        price.getBook().getId(),
                        targetBookId
                ))
                .forEach(price -> targetPricesByKey.put(
                        createPriceKey(price),
                        price
                ));

        List<ProviderBookReconciliationPreview.PriceConflict> conflicts =
                new ArrayList<>();

        for (EditorialPrice sourcePrice : sourcePrices) {
            EditorialPrice targetPrice = targetPricesByKey.get(
                    createPriceKey(sourcePrice)
            );

            if (targetPrice == null) {
                continue;
            }

            if (pricesEqual(
                    sourcePrice.getPrice(),
                    targetPrice.getPrice()
            )) {
                continue;
            }

            conflicts.add(
                    new ProviderBookReconciliationPreview.PriceConflict(
                            sourcePrice.getId(),
                            targetPrice.getId(),
                            sourcePrice.getValidFrom(),
                            sourcePrice.getPrice(),
                            targetPrice.getPrice()
                    )
            );
        }

        return new PriceAnalysis(
                sourcePrices,
                targetPricesByKey,
                conflicts
        );
    }

    private PriceMoveResult movePrices(
            PriceAnalysis analysis,
            Book targetBook
    ) {
        List<EditorialPrice> pricesToSave = new ArrayList<>();
        List<EditorialPrice> duplicatesToDelete = new ArrayList<>();

        int moved = 0;
        int removedDuplicates = 0;

        for (EditorialPrice sourcePrice : analysis.sourcePrices()) {
            PriceKey key = createPriceKey(sourcePrice);

            EditorialPrice targetPrice = analysis
                    .targetPricesByKey()
                    .get(key);

            if (targetPrice == null) {
                sourcePrice.setBook(targetBook);
                pricesToSave.add(sourcePrice);

                moved++;
                continue;
            }

            /*
             * Los precios diferentes ya fueron bloqueados antes.
             * Si llegamos acá, son precios iguales para la misma
             * fecha y el mismo origen.
             */
            duplicatesToDelete.add(sourcePrice);
            removedDuplicates++;
        }

        if (!pricesToSave.isEmpty()) {
            editorialPriceRepository.saveAll(pricesToSave);
        }

        if (!duplicatesToDelete.isEmpty()) {
            editorialPriceRepository.deleteAllInBatch(
                    duplicatesToDelete
            );
        }

        return new PriceMoveResult(
                moved,
                removedDuplicates
        );
    }

    private void mergeProviderBookData(
            ProviderBook source,
            ProviderBook target
    ) {
        /*
         * Priorizamos los datos del registro que el usuario
         * está conciliando, porque contienen lo reportado por
         * el proveedor para esa línea concreta.
         */
        if (hasText(source.getExternalCode())) {
            target.setExternalCode(source.getExternalCode());
        }

        if (hasText(source.getReportedIsbn())) {
            target.setReportedIsbn(source.getReportedIsbn());
        }

        target.setIdentifierStatus(
                ProviderBookIdentifierStatus.MANUALLY_CONFIRMED
        );

        target.setActive(true);

        if (source.getLastSeenAt() != null
                && (
                target.getLastSeenAt() == null
                        || source.getLastSeenAt().isAfter(
                        target.getLastSeenAt()
                )
        )) {
            target.setLastSeenAt(source.getLastSeenAt());
        }

        target.setUpdatedAt(Instant.now());
    }

    private boolean deletePreviousBookIfOrphan(Book book) {
        boolean hasInventory =
                inventoryRepository.existsByBookId(book.getId());

        boolean hasPrices =
                editorialPriceRepository.existsByBookId(book.getId());

        boolean hasProviderLinks =
                providerBookRepository.existsByBookId(book.getId());

        if (hasInventory || hasPrices || hasProviderLinks) {
            return false;
        }

        /*
         * Nunca eliminamos automáticamente libros cargados
         * manualmente o provenientes de un escaneo.
         */
        if (book.getSource() != BookSource.EDITORIAL_PRICE_LIST) {
            return false;
        }

        bookRepository.delete(book);

        return true;
    }

    private void validateDifferentBooks(
            Book currentBook,
            Book targetBook
    ) {
        if (Objects.equals(
                currentBook.getId(),
                targetBook.getId()
        )) {
            throw new BusinessException(
                    "El libro de origen y el libro de destino son el mismo."
            );
        }
    }

    private List<String> buildWarnings(
            Book currentBook,
            Book targetBook,
            ProviderBook existingTargetLink,
            List<ProviderBookReconciliationPreview.PriceConflict> conflicts
    ) {
        List<String> warnings = new ArrayList<>();

        if (!normalize(currentBook.getTitle()).equals(
                normalize(targetBook.getTitle())
        )) {
            warnings.add(
                    "Los títulos de los libros son diferentes."
            );
        }

        if (hasText(currentBook.getIsbn13())
                && hasText(targetBook.getIsbn13())
                && !currentBook.getIsbn13().equals(
                targetBook.getIsbn13()
        )) {
            warnings.add(
                    "Ambos libros tienen ISBN-13 diferentes."
            );
        }

        if (existingTargetLink != null) {
            warnings.add(
                    "El libro de destino ya está relacionado "
                            + "con este proveedor. Las relaciones se fusionarán."
            );
        }

        if (!conflicts.isEmpty()) {
            warnings.add(
                    "Existen precios diferentes para la misma fecha. "
                            + "La conciliación no puede confirmarse automáticamente."
            );
        }

        if (inventoryRepository.existsByBookId(
                currentBook.getId()
        )) {
            warnings.add(
                    "El libro de origen tiene inventario. "
                            + "No será eliminado después de la conciliación."
            );
        }

        return warnings;
    }

    private ProviderBookReconciliationPreview.ProviderBookSummary
    toProviderBookSummary(ProviderBook providerBook) {
        return new ProviderBookReconciliationPreview.ProviderBookSummary(
                providerBook.getId(),
                providerBook.getProvider().getId(),
                providerBook.getProvider().getName(),
                providerBook.getExternalCode(),
                providerBook.getReportedIsbn(),
                providerBook.getIdentifierStatus() != null
                        ? providerBook.getIdentifierStatus().name()
                        : null
        );
    }

    private ProviderBookReconciliationPreview.BookSummary
    toBookSummary(Book book) {
        return new ProviderBookReconciliationPreview.BookSummary(
                book.getId(),
                book.getTitle(),
                book.getIsbn10(),
                book.getIsbn13(),
                book.getPublisher() != null
                        ? book.getPublisher().getName()
                        : null,
                inventoryRepository.existsByBookId(book.getId())
        );
    }

    private PriceKey createPriceKey(EditorialPrice price) {
        return new PriceKey(price.getValidFrom());
    }

    private boolean pricesEqual(
            BigDecimal first,
            BigDecimal second
    ) {
        if (first == null || second == null) {
            return first == null && second == null;
        }

        return first.compareTo(second) == 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase();
    }

    private record PriceKey(
            LocalDate validFrom
    ) {
    }

    private record PriceAnalysis(
            List<EditorialPrice> sourcePrices,
            Map<PriceKey, EditorialPrice> targetPricesByKey,
            List<ProviderBookReconciliationPreview.PriceConflict> conflicts
    ) {
    }

    private record PriceMoveResult(
            int moved,
            int removedDuplicates
    ) {
    }
}