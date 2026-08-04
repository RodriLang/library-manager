package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.importer.price.configuration.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.importer.price.dto.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.isbn.model.IsbnParseStatus;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
public class PriceListIdentifierResolver {

    private final IsbnService isbnService;

    public PriceListIdentifier resolve(PriceListRow row) {
        String reportedIsbn = normalizeNullable(row.isbn());
        String externalCode = resolveExternalCode(row.metadata());

        ParsedIsbn reported = isbnService.parse(reportedIsbn);

        if (reported.valid()) {
            return PriceListIdentifier.isbn(
                    reported.isbn10(),
                    reported.isbn13(),
                    externalCode,
                    reportedIsbn,
                    mapStatus(reported.status())
            );
        }

        ParsedIsbn parsedExternalCode = isbnService.parse(externalCode);

        if (parsedExternalCode.valid()) {
            return PriceListIdentifier.isbn(
                    parsedExternalCode.isbn10(),
                    parsedExternalCode.isbn13(),
                    externalCode,
                    reportedIsbn,
                    ProviderBookIdentifierStatus.RECOVERED_FROM_CODE_COLUMN
            );
        }

        /*
         * Caso:
         *
         * Código: 9879065379
         * ISBN:   9789879065379
         *
         * El código es ISBN-10 válido, por lo que la rama anterior
         * ya devuelve el ISBN-13 correcto.
         */

        if (hasText(externalCode)) {
            return PriceListIdentifier.externalCode(
                    externalCode,
                    reportedIsbn,
                    ProviderBookIdentifierStatus.EXTERNAL_CODE
            );
        }

        if (hasText(reportedIsbn)) {
            return PriceListIdentifier.externalCode(
                    isbnService.normalize(reportedIsbn),
                    reportedIsbn,
                    ProviderBookIdentifierStatus.INVALID_UNRESOLVED
            );
        }

        return PriceListIdentifier.empty();
    }

    private ProviderBookIdentifierStatus mapStatus(
            IsbnParseStatus status
    ) {
        return switch (status) {
            case VALID -> ProviderBookIdentifierStatus.VALID_ISBN;

            case RECOVERED_MISSING_CHECK_DIGIT -> ProviderBookIdentifierStatus.RECOVERED_MISSING_CHECK_DIGIT;

            case RECOVERED_INVALID_X -> ProviderBookIdentifierStatus.RECOVERED_INVALID_X;

            case INVALID -> ProviderBookIdentifierStatus.INVALID_UNRESOLVED;
        };
    }

    private String resolveExternalCode(
            PriceListMetadata metadata
    ) {
        if (metadata == null || !hasText(metadata.externalCode())) {
            return null;
        }

        return isbnService.normalize(metadata.externalCode());
    }

    private String normalizeNullable(String value) {
        return hasText(value)
                ? isbnService.normalize(value)
                : null;
    }
}