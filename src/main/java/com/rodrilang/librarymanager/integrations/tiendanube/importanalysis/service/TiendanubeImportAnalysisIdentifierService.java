package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisParsedIdentifier;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisIdentifierService {

    private final IsbnService isbnService;
    private final BookRepository bookRepository;

    public TiendanubeImportAnalysisParsedIdentifier resolve(TiendanubeVariantResponse variant) {
        ParsedIsbn barcode = isbnService.parse(variant.barcode());

        if (barcode.valid()) {
            return new TiendanubeImportAnalysisParsedIdentifier(
                    barcode,
                    "BARCODE",
                    TiendanubeImportAnalysisMatchType.EXACT_BARCODE
            );
        }

        ParsedIsbn sku = isbnService.parse(variant.sku());

        if (sku.valid()) {
            return new TiendanubeImportAnalysisParsedIdentifier(
                    sku,
                    "SKU",
                    TiendanubeImportAnalysisMatchType.EXACT_SKU
            );
        }

        return null;
    }

    @Transactional(readOnly = true)
    public List<Book> findCatalogBooks(ParsedIsbn parsed) {
        Map<Long, Book> books = new LinkedHashMap<>();

        if (parsed.isbn13() != null) {
            bookRepository.findByIsbn13InAndActiveTrue(List.of(parsed.isbn13()))
                    .forEach(book -> books.put(book.getId(), book));
        }

        if (parsed.isbn10() != null) {
            bookRepository.findByIsbn10InAndActiveTrue(List.of(parsed.isbn10()))
                    .forEach(book -> books.put(book.getId(), book));
        }

        return new ArrayList<>(books.values());
    }
}
