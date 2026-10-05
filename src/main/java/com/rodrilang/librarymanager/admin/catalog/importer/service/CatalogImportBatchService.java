package com.rodrilang.librarymanager.admin.catalog.importer.service;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportJob;
import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportJobError;
import com.rodrilang.librarymanager.admin.catalog.importer.repository.CatalogImportJobErrorRepository;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.provider.catalog.enums.*;
import com.rodrilang.librarymanager.provider.catalog.model.ProviderBook;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.enums.*;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CatalogImportBatchService {
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final ProviderBookRepository providerBookRepository;
    private final CatalogImportJobErrorRepository errorRepository;
    private final IsbnService isbnService;

    @Transactional
    public CatalogImportBatchResult process(CatalogImportJob job, List<PriceListRow> rows) {
        int created=0,enriched=0,unchanged=0,conflicted=0,skipped=0,errors=0,linksCreated=0,linksUpdated=0;
        Provider provider = job.getProvider();
        for (PriceListRow row : rows) {
            try {
                RowResult r = processRow(job, provider, row);
                created += r.created ? 1 : 0;
                enriched += r.enriched ? 1 : 0;
                unchanged += (!r.created && !r.enriched && !r.conflicted && !r.skipped) ? 1 : 0;
                conflicted += r.conflicted ? 1 : 0;
                skipped += r.skipped ? 1 : 0;
                if (r.conflicted || r.skipped) errors++;
                linksCreated += r.linkCreated ? 1 : 0;
                linksUpdated += r.linkUpdated ? 1 : 0;
            } catch (Exception ex) {
                errors++;
                saveError(job, row, "ROW_ERROR", safe(ex));
            }
        }
        return new CatalogImportBatchResult(rows.size(),created,enriched,unchanged,conflicted,skipped,errors,linksCreated,linksUpdated);
    }

    private RowResult processRow(CatalogImportJob job, Provider provider, PriceListRow row) {
        ParsedIsbn parsed = isbnService.parse(row.isbn());
        String externalCode = text(row.metadata() != null ? row.metadata().externalCode() : null);
        Optional<ProviderBook> linked = externalCode == null ? Optional.empty() : providerBookRepository.findByProviderIdAndExternalCode(provider.getId(), externalCode);

        Book book = null;
        if (parsed.valid()) {
            book = bookRepository.findByIsbn13(parsed.isbn13()).orElse(null);
            if (book == null && parsed.isbn10() != null) book = bookRepository.findByIsbn10(parsed.isbn10()).orElse(null);
        }
        if (book == null && linked.isPresent()) book = linked.get().getBook();

        if (book == null && !parsed.valid()) {
            saveError(job,row,"REVIEW_REQUIRED","No se creó ni vinculó automáticamente porque la fila no tiene un ISBN válido ni un código externo ya conocido para este proveedor.");
            return RowResult.skippedResult();
        }
        if (book == null && text(row.title()) == null) {
            saveError(job,row,"REVIEW_REQUIRED","La fila tiene un ISBN válido pero no tiene título para crear el libro.");
            return RowResult.skippedResult();
        }

        boolean created=false,enriched=false,conflicted=false;
        if (book == null) {
            book = createBook(row, parsed);
            book = bookRepository.save(book);
            created=true;
        } else {
            if (text(row.title()) != null && text(book.getTitle()) != null
                    && !TextNormalizer.normalizeForMatch(row.title()).equals(TextNormalizer.normalizeForMatch(book.getTitle()))) {
                conflicted=true;
                saveError(job,row,"METADATA_CONFLICT","El ISBN coincide con un libro existente pero el título informado es diferente. Se conservó el título actual y sólo se completaron campos faltantes.");
            }
            enriched = enrich(book,row,parsed);
            if (enriched) bookRepository.save(book);
        }

        LinkResult link = upsertProviderBook(provider, book, row, parsed, externalCode);
        return new RowResult(created,enriched,conflicted,false,link.created,link.updated);
    }

    private Book createBook(PriceListRow row, ParsedIsbn parsed) {
        PriceListMetadata m=row.metadata();
        Book b=Book.builder()
                .isbn10(parsed.isbn10()).isbn13(parsed.isbn13()).title(row.title().trim())
                .subtitle(m!=null?text(m.subtitle()):null).description(m!=null?text(m.description()):null)
                .language(m!=null?text(m.language()):null).pageCount(m!=null?m.pageCount():null)
                .publicationYear(m!=null?m.publicationYear():null).publicationMonth(m!=null?m.publicationMonth():null)
                .categoryName(text(row.categoryName())).genreName(m!=null?text(m.genreName()):null)
                .collectionName(m!=null?text(m.collectionName()):null)
                .weightGrams(m!=null?m.weightGrams():null).widthCm(m!=null?m.widthCm():null)
                .heightCm(m!=null?m.heightCm():null).depthCm(m!=null?m.depthCm():null)
                .publisher(resolvePublisher(row.publisherName())).authors(resolveAuthors(row.authorName()))
                .source(BookSource.IMPORTED).catalogStatus(BookCatalogStatus.VERIFIED)
                .coverSearchStatus(CoverSearchStatus.PENDING).active(true).build();
        if (m!=null && text(m.sourceCoverUrl())!=null) b.registerCoverCandidate(m.sourceCoverUrl());
        return b;
    }

    private boolean enrich(Book b, PriceListRow row, ParsedIsbn parsed) {
        boolean changed=false; PriceListMetadata m=row.metadata();
        if (b.getIsbn13()==null && parsed.isbn13()!=null) { b.setIsbn13(parsed.isbn13()); changed=true; }
        if (b.getIsbn10()==null && parsed.isbn10()!=null) { b.setIsbn10(parsed.isbn10()); changed=true; }
        if (b.getPublisher()==null && text(row.publisherName())!=null) { b.setPublisher(resolvePublisher(row.publisherName())); changed=true; }
        if ((b.getAuthors()==null || b.getAuthors().isEmpty()) && text(row.authorName())!=null) { b.setAuthors(resolveAuthors(row.authorName())); changed=true; }
        if (b.getCategoryName()==null && text(row.categoryName())!=null) { b.setCategoryName(text(row.categoryName())); changed=true; }
        if (m==null) return changed;
        if (b.getSubtitle()==null && text(m.subtitle())!=null) { b.setSubtitle(text(m.subtitle())); changed=true; }
        if (b.getDescription()==null && text(m.description())!=null) { b.setDescription(text(m.description())); changed=true; }
        if (b.getGenreName()==null && text(m.genreName())!=null) { b.setGenreName(text(m.genreName())); changed=true; }
        if (b.getCollectionName()==null && text(m.collectionName())!=null) { b.setCollectionName(text(m.collectionName())); changed=true; }
        if (b.getLanguage()==null && text(m.language())!=null) { b.setLanguage(text(m.language())); changed=true; }
        if (b.getPageCount()==null && m.pageCount()!=null) { b.setPageCount(m.pageCount()); changed=true; }
        if (b.getPublicationYear()==null && m.publicationYear()!=null) { b.setPublicationYear(m.publicationYear()); changed=true; }
        if (b.getPublicationMonth()==null && m.publicationMonth()!=null) { b.setPublicationMonth(m.publicationMonth()); changed=true; }
        if (b.getWeightGrams()==null && m.weightGrams()!=null) { b.setWeightGrams(m.weightGrams()); changed=true; }
        if (b.getWidthCm()==null && m.widthCm()!=null) { b.setWidthCm(m.widthCm()); changed=true; }
        if (b.getHeightCm()==null && m.heightCm()!=null) { b.setHeightCm(m.heightCm()); changed=true; }
        if (b.getDepthCm()==null && m.depthCm()!=null) { b.setDepthCm(m.depthCm()); changed=true; }
        if ((b.getCoverUrl()==null || b.getCoverUrl().isBlank()) && text(m.sourceCoverUrl())!=null) { b.registerCoverCandidate(m.sourceCoverUrl()); changed=true; }
        return changed;
    }

    private Publisher resolvePublisher(String raw) {
        String name=text(raw); if (name==null) return null;
        String normalized=TextNormalizer.normalizeForMatch(name);
        return publisherRepository.findByNameNormalized(normalized).orElseGet(() -> publisherRepository.save(Publisher.builder().name(name).build()));
    }

    private Set<Author> resolveAuthors(String raw) {
        String value=text(raw); if (value==null) return new LinkedHashSet<>();
        Set<Author> result=new LinkedHashSet<>();
        for (String part : value.split("\\s*(?:;|\\|)\\s*")) {
            String name=text(part); if (name==null) continue;
            String normalized=TextNormalizer.normalizeForMatch(name);
            result.add(authorRepository.findByNameNormalized(normalized).orElseGet(() -> authorRepository.save(Author.builder().name(name).build())));
        }
        return result;
    }

    private LinkResult upsertProviderBook(Provider provider, Book book, PriceListRow row, ParsedIsbn parsed, String externalCode) {
        ProviderBook pb=providerBookRepository.findByProviderIdAndBookId(provider.getId(),book.getId()).orElse(null);
        Instant now=Instant.now(); boolean created=false,updated=false;
        if (pb==null) {
            pb=ProviderBook.builder().provider(provider).book(book).externalCode(externalCode).reportedIsbn(text(row.isbn()))
                    .identifierStatus(identifierStatus(parsed,externalCode)).active(true).firstSeenAt(now).lastSeenAt(now)
                    .source(ProviderBookSource.ADMIN_IMPORT).verificationStatus(ProviderBookVerificationStatus.VERIFIED)
                    .createdAt(now).updatedAt(now).build(); created=true;
        } else {
            if (externalCode!=null && !Objects.equals(pb.getExternalCode(),externalCode)) { pb.setExternalCode(externalCode); updated=true; }
            if (text(row.isbn())!=null && !Objects.equals(pb.getReportedIsbn(),text(row.isbn()))) { pb.setReportedIsbn(text(row.isbn())); updated=true; }
            pb.setIdentifierStatus(identifierStatus(parsed,externalCode)); pb.setActive(true); pb.setLastSeenAt(now);
            pb.setSource(ProviderBookSource.ADMIN_IMPORT); pb.setVerificationStatus(ProviderBookVerificationStatus.VERIFIED); pb.setUpdatedAt(now); updated=true;
        }
        providerBookRepository.save(pb); return new LinkResult(created,!created && updated);
    }

    private ProviderBookIdentifierStatus identifierStatus(ParsedIsbn p,String code) {
        if (p.valid()) return switch (p.status()) {
            case VALID -> p.isbn10()!=null ? ProviderBookIdentifierStatus.RECOVERED_FROM_ISBN10 : ProviderBookIdentifierStatus.VALID_ISBN;
            case RECOVERED_MISSING_CHECK_DIGIT -> ProviderBookIdentifierStatus.RECOVERED_MISSING_CHECK_DIGIT;
            case RECOVERED_INVALID_X -> ProviderBookIdentifierStatus.RECOVERED_INVALID_X;
            default -> ProviderBookIdentifierStatus.VALID_ISBN;
        };
        return code!=null ? ProviderBookIdentifierStatus.EXTERNAL_CODE : ProviderBookIdentifierStatus.NO_IDENTIFIER;
    }

    private void saveError(CatalogImportJob job, PriceListRow row, String type, String message) {
        errorRepository.save(CatalogImportJobError.builder().job(job).rowNumber(row.rowNumber()).isbn(text(row.isbn()))
                .title(text(row.title())).errorType(type).message(message).createdAt(Instant.now()).build());
    }
    private String text(String s){ return s==null||s.isBlank()?null:s.trim(); }
    private String safe(Exception e){ return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    private record RowResult(boolean created,boolean enriched,boolean conflicted,boolean skipped,boolean linkCreated,boolean linkUpdated){ static RowResult skippedResult(){return new RowResult(false,false,false,true,false,false);} }
    private record LinkResult(boolean created,boolean updated){}
}
