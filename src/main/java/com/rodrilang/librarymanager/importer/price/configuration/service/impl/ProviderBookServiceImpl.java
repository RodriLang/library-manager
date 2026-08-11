package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.dto.response.BookProviderResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.ProviderBookRegistrationResult;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookBatchRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.importer.price.configuration.service.ProviderBookService;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.ProviderBookUpsertRow;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
import com.rodrilang.librarymanager.model.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class ProviderBookServiceImpl implements ProviderBookService {

    private final ProviderBookRepository providerBookRepository;
    private final ProviderBookBatchRepository providerBookBatchRepository;
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
    @Transactional(readOnly = true)
    public List<BookProviderResponse> findActiveProvidersByBookId(
            Long bookId
    ) {
        return providerBookRepository.findActiveProvidersByBookId(bookId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookProviderResponse> getProvidersForBook(Long bookId) {
        return providerBookRepository.findActiveProvidersByBookId(bookId);
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
            throw new BusinessException("La cantidad de libros no coincide con la cantidad de filas importadas.");
        }

        Instant now = Instant.now();

        Map<Long, ProviderBookUpsertRow> upsertRowsByBookId = new LinkedHashMap<>();

        Map<String, Long> bookIdByExternalCode = new HashMap<>();

        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            PriceListIdentifier identifier = identifierResolver.resolve(rows.get(i));

            if (book.getId() == null) {
                throw new IllegalStateException(
                        "No se puede registrar un libro sin ID."
                );
            }

            String externalCode = normalizeNullable(identifier.externalCode());

            if (externalCode != null) {
                Long previousBookId =
                        bookIdByExternalCode.putIfAbsent(
                                externalCode,
                                book.getId()
                        );

                if (previousBookId != null && !previousBookId.equals(book.getId())) {
                    throw new BusinessException(
                            "El código externo "
                                    + externalCode
                                    + " está asociado a más de un libro del lote: "
                                    + previousBookId
                                    + " y "
                                    + book.getId()
                                    + "."
                    );
                }
            }

            upsertRowsByBookId.putIfAbsent(
                    book.getId(),
                    new ProviderBookUpsertRow(
                            book.getId(),
                            externalCode,
                            identifier.reportedIsbn(),
                            identifier.status()
                    )
            );
        }

        try {
            providerBookBatchRepository.upsertBatch(
                    provider.getId(),
                    new ArrayList<>(upsertRowsByBookId.values()),
                    now
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    "No se pudieron registrar las referencias del proveedor. "
                            + "Verifique que ningún código externo esté asociado a más de un libro."
            );
        }
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
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