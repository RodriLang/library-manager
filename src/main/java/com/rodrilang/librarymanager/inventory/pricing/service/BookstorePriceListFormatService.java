package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.pricing.dto.BookstorePriceListFormatRequest;
import com.rodrilang.librarymanager.inventory.pricing.dto.BookstorePriceListFormatResponse;
import com.rodrilang.librarymanager.inventory.pricing.model.BookstorePriceListFormat;
import com.rodrilang.librarymanager.inventory.pricing.repository.BookstorePriceListFormatRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.service.ProviderAccessService;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookstorePriceListFormatService {

    private static final String STANDARD_NAME = "Formato Anaquel";

    private final BookstorePriceListFormatRepository repository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreContext bookstoreContext;
    private final BookstoreProviderRepository bookstoreProviderRepository;
    private final ProviderAccessService providerAccessService;

    @Transactional
    public List<BookstorePriceListFormatResponse> list() {
        return list(null);
    }

    @Transactional
    public List<BookstorePriceListFormatResponse> list(Long providerId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        ensureStandard(bookstoreId);
        if (providerId != null) {
            providerAccessService.requireUsableByBookstore(providerId, bookstoreId);
        }
        List<BookstorePriceListFormat> formats = providerId == null
                ? repository.findAllByBookstoreIdAndActiveTrueOrderByStandardDescNameAsc(bookstoreId)
                : repository.findAllByBookstoreIdAndProviderIdAndActiveTrueOrderByNameAsc(bookstoreId, providerId);
        return formats.stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookstorePriceListFormatResponse create(BookstorePriceListFormatRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        validate(request);
        if (repository.existsByBookstoreIdAndNameIgnoreCase(bookstoreId, request.name().trim())) {
            throw new BusinessException("Ya existe un formato de lista con ese nombre.");
        }

        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería seleccionada."));

        Provider provider = resolveProvider(request.providerId(), bookstoreId);
        ensureBookstoreProvider(bookstore, provider);

        BookstorePriceListFormat entity = BookstorePriceListFormat.builder()
                .bookstore(bookstore)
                .provider(provider)
                .name(request.name().trim())
                .standard(false)
                .sheetIndex(request.sheetIndex())
                .firstDataRowIndex(request.firstDataRowIndex())
                .isbnColumn(request.isbnColumn())
                .titleColumn(request.titleColumn())
                .authorColumn(request.authorColumn())
                .publisherColumn(request.publisherColumn())
                .priceColumn(request.priceColumn())
                .active(true)
                .build();

        return toResponse(repository.save(entity));
    }

    @Transactional
    public BookstorePriceListFormatResponse update(Long id, BookstorePriceListFormatRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        validate(request);
        BookstorePriceListFormat entity = getEntity(id, bookstoreId);
        if (entity.isStandard()) {
            throw new BusinessException("El formato Anaquel no se puede modificar.");
        }

        Provider provider = resolveProvider(request.providerId(), bookstoreId);
        ensureBookstoreProvider(entity.getBookstore(), provider);
        entity.setProvider(provider);
        entity.setName(request.name().trim());
        entity.setSheetIndex(request.sheetIndex());
        entity.setFirstDataRowIndex(request.firstDataRowIndex());
        entity.setIsbnColumn(request.isbnColumn());
        entity.setTitleColumn(request.titleColumn());
        entity.setAuthorColumn(request.authorColumn());
        entity.setPublisherColumn(request.publisherColumn());
        entity.setPriceColumn(request.priceColumn());
        return toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstorePriceListFormat entity = getEntity(id, bookstoreId);
        if (entity.isStandard()) {
            throw new BusinessException("El formato Anaquel no se puede eliminar.");
        }
        entity.setActive(false);
    }

    @Transactional
    public BookstorePriceListFormat getForCurrentBookstore(Long id) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        ensureStandard(bookstoreId);
        BookstorePriceListFormat format = getEntity(id, bookstoreId);
        if (format.getProvider() != null) {
            providerAccessService.requireUsableByBookstore(format.getProvider().getId(), bookstoreId);
        }
        return format;
    }

    private BookstorePriceListFormat getEntity(Long id, Long bookstoreId) {
        return repository.findByIdAndBookstoreId(id, bookstoreId)
                .filter(BookstorePriceListFormat::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el formato de lista solicitado."));
    }

    private void ensureStandard(Long bookstoreId) {
        if (repository.findByBookstoreIdAndStandardTrue(bookstoreId).isPresent()) {
            return;
        }
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería seleccionada."));

        repository.save(BookstorePriceListFormat.builder()
                .bookstore(bookstore)
                .name(STANDARD_NAME)
                .standard(true)
                .sheetIndex(0)
                .firstDataRowIndex(1)
                .isbnColumn(0)
                .titleColumn(1)
                .authorColumn(2)
                .publisherColumn(3)
                .priceColumn(4)
                .active(true)
                .build());
    }

    private void validate(BookstorePriceListFormatRequest request) {
        boolean hasIsbn = request.isbnColumn() != null;
        boolean hasTitleAuthor = request.titleColumn() != null && request.authorColumn() != null;
        if (!hasIsbn && !hasTitleAuthor) {
            throw new BusinessException("El formato debe identificar el libro por ISBN o por título y autor.");
        }
    }

    private BookstorePriceListFormatResponse toResponse(BookstorePriceListFormat entity) {
        return new BookstorePriceListFormatResponse(
                entity.getId(),
                entity.getProvider() != null ? entity.getProvider().getId() : null,
                entity.getProvider() != null ? entity.getProvider().getName() : null,
                entity.getName(), entity.isStandard(), entity.getSheetIndex(),
                entity.getFirstDataRowIndex(), entity.getIsbnColumn(), entity.getTitleColumn(),
                entity.getAuthorColumn(), entity.getPublisherColumn(), entity.getPriceColumn(), entity.isActive()
        );
    }

    private void ensureBookstoreProvider(Bookstore bookstore, Provider provider) {
        if (provider == null) {
            return;
        }
        BookstoreProvider relation = bookstoreProviderRepository
                .findByBookstoreIdAndProviderId(bookstore.getId(), provider.getId())
                .orElseGet(() -> BookstoreProvider.builder()
                        .bookstore(bookstore)
                        .provider(provider)
                        .active(true)
                        .build());
        relation.setActive(true);
        bookstoreProviderRepository.save(relation);
    }

    private Provider resolveProvider(Long providerId, Long bookstoreId) {
        if (providerId == null) {
            return null;
        }
        return providerAccessService.requireUsableByBookstore(providerId, bookstoreId);
    }
}
