package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.ProviderBookRegistrationResult;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookService;
import com.rodrilang.librarymanager.importer.price.dto.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
import com.rodrilang.librarymanager.model.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class ProviderBookServiceImpl implements ProviderBookService {

    private final ProviderBookRepository providerBookRepository;
    private final PriceListIdentifierResolver identifierResolver;

    @Override
    @Transactional
    public ProviderBookRegistrationResult registerOrUpdate(
            PriceListProvider provider,
            Book book,
            String externalCode
    ) {
        ProviderBook providerBook = providerBookRepository
                .findByProviderIdAndBookId(provider.getId(), book.getId())
                .orElse(null);

        boolean created = providerBook == null;
        Instant now = Instant.now();

        if (created) {
            providerBook = ProviderBook.builder()
                    .provider(provider)
                    .book(book)
                    .createdAt(now)
                    .build();
        }

        if (hasText(externalCode)) {
            validateExternalCodeAvailability(provider.getId(), book.getId(), externalCode);
            providerBook.setExternalCode(externalCode.trim());
        }

        providerBook.setActive(true);
        providerBook.setLastSeenAt(now);
        providerBook.setUpdatedAt(now);

        ProviderBook saved = providerBookRepository.save(providerBook);

        return new ProviderBookRegistrationResult(
                saved.getId(),
                provider.getId(),
                book.getId(),
                saved.getExternalCode(),
                created
        );
    }

    @Override
    @Transactional
    public void registerBatch(
            PriceListProvider provider,
            List<Book> books,
            List<PriceListRow> rows
    ) {
        if (provider == null || books == null || books.isEmpty()) {
            return;
        }

        if (rows == null || books.size() != rows.size()) {
            throw new BusinessException(
                    "La cantidad de libros no coincide con la cantidad de filas importadas."
            );
        }

        Long providerId = provider.getId();
        Instant now = Instant.now();

        List<PriceListIdentifier> identifiers = rows.stream()
                .map(identifierResolver::resolve)
                .toList();

        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, ProviderBook> existingByBookId = providerBookRepository
                .findByProviderIdAndBookIdIn(providerId, bookIds)
                .stream()
                .collect(Collectors.toMap(
                        providerBook -> providerBook.getBook().getId(),
                        Function.identity()
                ));

        Set<String> externalCodes = identifiers.stream()
                .map(PriceListIdentifier::externalCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, ProviderBook> existingByExternalCode =
                externalCodes.isEmpty()
                        ? new HashMap<>()
                        : providerBookRepository
                        .findByProviderIdAndExternalCodeIn(
                                providerId,
                                externalCodes
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                ProviderBook::getExternalCode,
                                Function.identity(),
                                (first, repeated) -> first,
                                HashMap::new
                        ));

        /*
         * También contiene las relaciones nuevas creadas durante
         * este mismo lote, para detectar códigos repetidos sin
         * hacer consultas adicionales.
         */
        Map<String, ProviderBook> ownersByExternalCode =
                new HashMap<>(existingByExternalCode);

        Map<Long, ProviderBook> toSaveByBookId = new LinkedHashMap<>();

        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            PriceListIdentifier identifier = identifiers.get(i);

            ProviderBook providerBook =
                    existingByBookId.get(book.getId());

            boolean changed = false;

            if (providerBook == null) {
                providerBook = ProviderBook.builder()
                        .provider(provider)
                        .book(book)
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .lastSeenAt(now)
                        .build();

                existingByBookId.put(book.getId(), providerBook);
                changed = true;
            }

            String newExternalCode = normalizeNullable(
                    identifier.externalCode()
            );

            String previousExternalCode = normalizeNullable(
                    providerBook.getExternalCode()
            );

            if (newExternalCode != null) {
                ProviderBook owner =
                        ownersByExternalCode.get(newExternalCode);

                if (owner != null
                        && !owner.getBook().getId().equals(book.getId())) {
                    throw new BusinessException(
                            "El código externo "
                                    + newExternalCode
                                    + " está asociado a más de un libro del proveedor: "
                                    + owner.getBook().getId()
                                    + " y "
                                    + book.getId()
                                    + "."
                    );
                }
            }

            if (!Objects.equals(
                    previousExternalCode,
                    newExternalCode
            )) {
                if (previousExternalCode != null) {
                    ProviderBook previousOwner =
                            ownersByExternalCode.get(previousExternalCode);

                    if (previousOwner == providerBook) {
                        ownersByExternalCode.remove(previousExternalCode);
                    }
                }

                providerBook.setExternalCode(newExternalCode);
                changed = true;
            }

            if (newExternalCode != null) {
                ownersByExternalCode.put(
                        newExternalCode,
                        providerBook
                );
            }

            if (!Objects.equals(
                    providerBook.getReportedIsbn(),
                    identifier.reportedIsbn()
            )) {
                providerBook.setReportedIsbn(
                        identifier.reportedIsbn()
                );
                changed = true;
            }

            if (providerBook.getIdentifierStatus()
                    != identifier.status()) {
                providerBook.setIdentifierStatus(
                        identifier.status()
                );
                changed = true;
            }

            if (!providerBook.isActive()) {
                providerBook.setActive(true);
                changed = true;
            }

            /*
             * Para una importación inicial casi todos serán nuevos.
             * En reimportaciones, actualizar lastSeenAt obliga a
             * guardar la relación aunque los demás datos no cambien.
             */
            providerBook.setLastSeenAt(now);

            if (changed) {
                providerBook.setUpdatedAt(now);
                toSaveByBookId.put(
                        book.getId(),
                        providerBook
                );
            }
        }

        if (!toSaveByBookId.isEmpty()) {
            providerBookRepository.saveAll(
                    toSaveByBookId.values()
            );
        }
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private boolean equalsNullable(
            String first,
            String second
    ) {
        return java.util.Objects.equals(first, second);
    }

    private void validateExternalCodeAvailability(Long providerId, Long bookId, String externalCode) {
        providerBookRepository.findByProviderIdAndExternalCode(providerId, externalCode.trim())
                .filter(existing -> !existing.getBook().getId().equals(bookId))
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "El código externo " + externalCode
                                    + " ya está asociado a otro libro del proveedor."
                    );
                });
    }
}