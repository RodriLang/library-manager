package com.rodrilang.librarymanager.catalog.contribution.service;

import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.catalog.contribution.dto.BookFieldOverrideResponse;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldSource;
import com.rodrilang.librarymanager.catalog.contribution.model.BookFieldProposal;
import com.rodrilang.librarymanager.catalog.contribution.model.BookstoreBookFieldOverride;
import com.rodrilang.librarymanager.catalog.contribution.repository.BookFieldProposalRepository;
import com.rodrilang.librarymanager.catalog.contribution.repository.BookstoreBookFieldOverrideRepository;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.service.PublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookstoreBookFieldOverrideService {

    private final BookstoreBookFieldOverrideRepository overrideRepository;
    private final BookFieldProposalRepository proposalRepository;
    private final BookFieldValueService fieldValueService;
    private final BookstoreContext bookstoreContext;
    private final PublisherService publisherService;
    private final AuthorService authorService;

    @Transactional(readOnly = true)
    public Optional<BookstoreBookFieldOverride> findOverride(Long bookstoreId, Long bookId, BookField field) {
        return overrideRepository.findByBookstoreIdAndBook_IdAndField(bookstoreId, bookId, field);
    }

    @Transactional
    public BookstoreBookFieldOverride upsert(
            Book book,
            BookField field,
            String value,
            Long bookstoreId,
            Long userId
    ) {
        BookstoreBookFieldOverride override = overrideRepository
                .findByBookstoreIdAndBook_IdAndField(bookstoreId, book.getId(), field)
                .orElseGet(() -> BookstoreBookFieldOverride.builder()
                        .bookstoreId(bookstoreId)
                        .book(book)
                        .field(field)
                        .build());

        override.setValue(value);
        override.setUpdatedByUserId(userId);
        return overrideRepository.save(override);
    }

    @Transactional
    public void remove(Long bookstoreId, Long bookId, BookField field) {
        overrideRepository.deleteByBookstoreIdAndBook_IdAndField(bookstoreId, bookId, field);
    }

    @Transactional
    public int removeRedundantOverrides(Long bookId, BookField field, String approvedValue) {
        return overrideRepository.deleteRedundantOverrides(bookId, field, approvedValue);
    }

    @Transactional(readOnly = true)
    public BookstoreBookEffectiveValues resolve(Book book) {
        Long bookstoreId = currentBookstoreIdOrNull();
        return resolve(book, bookstoreId);
    }

    @Transactional(readOnly = true)
    public BookstoreBookEffectiveValues resolve(Book book, Long bookstoreId) {
        Map<BookField, BookstoreBookFieldOverride> overrides = bookstoreId == null
                ? Map.of()
                : overrideRepository.findAllByBookstoreIdAndBook_Id(bookstoreId, book.getId()).stream()
                .collect(Collectors.toMap(
                        BookstoreBookFieldOverride::getField,
                        Function.identity(),
                        (left, right) -> right,
                        () -> new EnumMap<>(BookField.class)
                ));

        String title = book.getTitle();
        String subtitle = book.getSubtitle();
        String description = book.getDescription();
        String language = book.getLanguage();
        Integer pageCount = book.getPageCount();
        Integer publicationYear = book.getPublicationYear();
        Integer publicationMonth = book.getPublicationMonth();
        String coverUrl = book.getCoverUrl();
        String categoryName = book.getCategoryName();
        String genreName = book.getGenreName();
        Publisher publisher = book.getPublisher();
        Set<Author> authors = book.getAuthors();
        BigDecimal weightGrams = book.getWeightGrams();
        BigDecimal widthCm = book.getWidthCm();
        BigDecimal heightCm = book.getHeightCm();
        BigDecimal depthCm = book.getDepthCm();

        for (BookstoreBookFieldOverride override : overrides.values()) {
            String value = override.getValue();
            switch (override.getField()) {
                case TITLE -> title = value;
                case SUBTITLE -> subtitle = value;
                case DESCRIPTION -> description = value;
                case LANGUAGE -> language = value;
                case PAGE_COUNT -> pageCount = Integer.valueOf(value);
                case PUBLICATION_YEAR -> publicationYear = Integer.valueOf(value);
                case PUBLICATION_MONTH -> publicationMonth = Integer.valueOf(value);
                case COVER_URL -> coverUrl = value;
                case CATEGORY_NAME -> categoryName = value;
                case GENRE_NAME -> genreName = value;
                case PUBLISHER -> publisher = publisherService.getEntityById(Long.valueOf(value));
                case AUTHORS -> authors = authorService.getEntitiesByIds(fieldValueService.readAuthorIds(value));
                case WEIGHT_GRAMS -> weightGrams = new BigDecimal(value);
                case WIDTH_CM -> widthCm = new BigDecimal(value);
                case HEIGHT_CM -> heightCm = new BigDecimal(value);
                case DEPTH_CM -> depthCm = new BigDecimal(value);
            }
        }

        if (publicationYear == null) {
            publicationMonth = null;
        }

        return new BookstoreBookEffectiveValues(
                title,
                subtitle,
                description,
                language,
                pageCount,
                publicationYear,
                publicationMonth,
                coverUrl,
                categoryName,
                genreName,
                publisher,
                authors == null ? Set.of() : authors,
                weightGrams,
                widthCm,
                heightCm,
                depthCm,
                Collections.unmodifiableMap(overrides)
        );
    }

    @Transactional(readOnly = true)
    public BookDetailResponse applyToDetail(BookDetailResponse base, Book globalBook) {
        Long bookstoreId = currentBookstoreIdOrNull();
        if (bookstoreId == null) {
            return withOverrideMetadata(base, Map.of(), base.fieldSources());
        }

        BookstoreBookEffectiveValues values = resolve(globalBook, bookstoreId);
        if (values.overrides().isEmpty()) {
            return withOverrideMetadata(base, Map.of(), base.fieldSources());
        }

        Map<String, BookFieldSource> effectiveSources = new LinkedHashMap<>();
        if (base.fieldSources() != null) {
            effectiveSources.putAll(base.fieldSources());
        }
        values.overrides().keySet().forEach(field -> effectiveSources.put(field.key(), BookFieldSource.BOOKSTORE_OVERRIDE));

        Map<String, BookFieldOverrideResponse> overrideMetadata = buildOverrideMetadata(
                globalBook,
                bookstoreId,
                values.overrides()
        );

        return new BookDetailResponse(
                base.id(),
                base.isbn(),
                values.title(),
                values.subtitle(),
                values.description(),
                values.language(),
                values.pageCount(),
                values.publicationYear(),
                values.publicationMonth(),
                values.coverUrl(),
                values.isOverridden(BookField.COVER_URL) ? BookFieldSource.BOOKSTORE_OVERRIDE.name() : base.coverSource(),
                values.categoryName(),
                values.genreName(),
                values.weightGrams(),
                values.widthCm(),
                values.heightCm(),
                values.depthCm(),
                base.source(),
                base.catalogStatus(),
                base.active(),
                toPublisherResponse(values.publisher()),
                toAuthorResponses(values.authors()),
                Collections.unmodifiableMap(effectiveSources),
                Collections.unmodifiableMap(overrideMetadata),
                base.providers(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    @Transactional(readOnly = true)
    public BookSummaryResponse applyToSummary(BookSummaryResponse base, Book globalBook) {
        Long bookstoreId = currentBookstoreIdOrNull();
        if (bookstoreId == null) {
            return base;
        }
        BookstoreBookEffectiveValues values = resolve(globalBook, bookstoreId);
        if (values.overrides().isEmpty()) {
            return base;
        }
        return new BookSummaryResponse(
                base.id(),
                base.isbn(),
                values.title(),
                values.coverUrl(),
                values.publisher() != null ? values.publisher().getName() : null,
                toAuthorResponses(values.authors())
        );
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse applyToInventorySummary(InventorySummaryResponse base, Book globalBook) {
        Long bookstoreId = currentBookstoreIdOrNull();
        if (bookstoreId == null) {
            return base;
        }
        BookstoreBookEffectiveValues values = resolve(globalBook, bookstoreId);
        if (values.overrides().isEmpty()) {
            return base;
        }
        return new InventorySummaryResponse(
                base.id(),
                base.bookId(),
                base.isbn(),
                values.title(),
                values.authors().stream().map(Author::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                values.publisher() != null ? values.publisher().getName() : null,
                values.coverUrl(),
                base.stock(),
                base.minimumStock(),
                base.consignmentStock(),
                base.ownedStock(),
                base.consignmentProviderId(),
                base.consignmentProviderName(),
                base.condition(),
                base.salePrice(),
                base.currentPriceEffectiveFrom(),
                base.currentPriceLastConfirmedAt(),
                base.currentPriceLastConfirmedSource(),
                base.nextSalePrice(),
                base.nextPriceEffectiveFrom(),
                base.lastPriceCheckedAt(),
                base.active()
        );
    }

    private BookDetailResponse withOverrideMetadata(
            BookDetailResponse base,
            Map<String, BookFieldOverrideResponse> overrides,
            Map<String, BookFieldSource> sources
    ) {
        return new BookDetailResponse(
                base.id(), base.isbn(), base.title(), base.subtitle(), base.description(), base.language(),
                base.pageCount(), base.publicationYear(), base.publicationMonth(), base.coverUrl(), base.coverSource(),
                base.categoryName(), base.genreName(), base.weightGrams(), base.widthCm(), base.heightCm(), base.depthCm(),
                base.source(), base.catalogStatus(), base.active(), base.publisher(), base.authors(), sources,
                overrides, base.providers(), base.createdAt(), base.updatedAt()
        );
    }

    private Map<String, BookFieldOverrideResponse> buildOverrideMetadata(
            Book globalBook,
            Long bookstoreId,
            Map<BookField, BookstoreBookFieldOverride> overrides
    ) {
        List<BookFieldProposal> proposals = proposalRepository
                .findAllByBook_IdAndSubmittedByBookstoreIdOrderByCreatedAtDesc(globalBook.getId(), bookstoreId);

        Map<String, BookFieldOverrideResponse> result = new LinkedHashMap<>();
        Map<String, BookFieldSource> catalogSources = globalBook.getEffectiveFieldSources();

        for (BookField field : BookField.values()) {
            BookstoreBookFieldOverride override = overrides.get(field);
            if (override == null) {
                continue;
            }

            BookFieldProposal relatedProposal = proposals.stream()
                    .filter(p -> p.getField() == field)
                    .filter(p -> Objects.equals(p.getProposedValue(), override.getValue()))
                    .findFirst()
                    .orElse(null);

            String catalogValue = fieldValueService.serializeCurrent(globalBook, field);
            result.put(field.key(), new BookFieldOverrideResponse(
                    field,
                    override.getValue(),
                    fieldValueService.displaySerialized(field, override.getValue()),
                    catalogValue,
                    fieldValueService.displaySerialized(field, catalogValue),
                    catalogSources.get(field.key()),
                    relatedProposal != null ? relatedProposal.getId() : null,
                    relatedProposal != null ? relatedProposal.getStatus() : null,
                    override.getUpdatedAt()
            ));
        }

        return result;
    }

    private PublisherResponse toPublisherResponse(Publisher publisher) {
        return publisher == null ? null : new PublisherResponse(publisher.getId(), publisher.getName());
    }

    private Set<AuthorResponse> toAuthorResponses(Set<Author> authors) {
        if (authors == null || authors.isEmpty()) {
            return Set.of();
        }
        return authors.stream()
                .sorted(Comparator.comparing(Author::getName, String.CASE_INSENSITIVE_ORDER))
                .map(author -> new AuthorResponse(author.getId(), author.getName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Long currentBookstoreIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
            return null;
        }
        return bookstoreContext.getCurrentBookstoreIdOrNull();
    }
}
