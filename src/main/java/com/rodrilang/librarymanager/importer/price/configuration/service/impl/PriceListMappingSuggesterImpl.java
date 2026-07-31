package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListSuggestedMappingResponse;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListMappingSuggester;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class PriceListMappingSuggesterImpl
        implements PriceListMappingSuggester {

    @Override
    public List<PriceListSuggestedMappingResponse> suggest(
            PriceListPreviewRowResponse headerRow
    ) {
        return java.util.stream.IntStream
                .range(0, headerRow.cells().size())
                .mapToObj(columnIndex ->
                        buildSuggestion(
                                columnIndex,
                                headerRow.cells().get(columnIndex)
                        )
                )
                .toList();
    }

    private PriceListSuggestedMappingResponse buildSuggestion(
            int columnIndex,
            String header
    ) {
        PriceListField field = detectField(header);

        return new PriceListSuggestedMappingResponse(
                columnIndex,
                header,
                field,
                field != null
                        ? resolveValueType(field)
                        : null
        );
    }

    private PriceListField detectField(String header) {
        String value = normalize(header);

        if (value.isBlank()) {
            return null;
        }

        if (matchesAny(
                value,
                "isbn",
                "ean",
                "codigo de barras",
                "cod barras",
                "cod barra"
        )) {
            return PriceListField.ISBN;
        }

        if (matchesAny(
                value,
                "titulo",
                "nombre del libro"
        )) {
            return PriceListField.TITLE;
        }

        if (matchesAny(
                value,
                "subtitulo"
        )) {
            return PriceListField.SUBTITLE;
        }

        if (matchesAny(
                value,
                "autor",
                "autores"
        )) {
            return PriceListField.AUTHOR;
        }

        if (matchesAny(
                value,
                "editorial",
                "sello",
                "sello editorial"
        )) {
            return PriceListField.PUBLISHER;
        }

        if (matchesAny(
                value,
                "pvp",
                "precio",
                "precio venta",
                "precio de venta"
        ) || value.startsWith("pvp ")) {
            return PriceListField.RETAIL_PRICE;
        }

        if (matchesAny(
                value,
                "categoria",
                "rubro"
        )) {
            return PriceListField.CATEGORY;
        }

        if (matchesAny(
                value,
                "tags",
                "etiquetas",
                "temas etiquetas"
        )) {
            return PriceListField.TAGS;
        }

        if (matchesAny(
                value,
                "genero",
                "tema"
        )) {
            return PriceListField.GENRE;
        }

        if (matchesAny(
                value,
                "descripcion",
                "sinopsis",
                "resena"
        )) {
            return PriceListField.DESCRIPTION;
        }

        if (matchesAny(
                value,
                "paginas",
                "pag"
        )) {
            return PriceListField.PAGE_COUNT;
        }

        if (matchesAny(
                value,
                "fecha publicacion",
                "fecha de publicacion"
        )) {
            return PriceListField.PUBLICATION_DATE;
        }

        if (matchesAny(
                value,
                "idioma"
        )) {
            return PriceListField.LANGUAGE;
        }

        if (matchesAny(
                value,
                "portada",
                "imagen de tapa",
                "imagen tapa",
                "cover"
        )) {
            return PriceListField.COVER_URL;
        }

        if (matchesAny(
                value,
                "coleccion"
        )) {
            return PriceListField.COLLECTION;
        }

        if (matchesAny(
                value,
                "medidas",
                "dimensiones",
                "tamano",
                "tamano cms",
                "tamano cm"
        )) {
            return PriceListField.DIMENSIONS;
        }

        if (matchesAny(
                value,
                "peso",
                "peso kg",
                "peso kgs"
        )) {
            return PriceListField.WEIGHT;
        }

        if (matchesAny(
                value,
                "stock",
                "stocks",
                "disponibilidad"
        )) {
            return PriceListField.EXTERNAL_STOCK;
        }

        if (matchesAny(
                value,
                "observaciones",
                "observacion"
        )) {
            return PriceListField.OBSERVATIONS;
        }

        if (matchesAny(
                value,
                "codigo",
                "cod"
        )) {
            return PriceListField.EXTERNAL_CODE;
        }

        return null;
    }

    private PriceListValueType resolveValueType(
            PriceListField field
    ) {
        return switch (field) {

            case ISBN -> PriceListValueType.ISBN;

            case RETAIL_PRICE,
                 WEIGHT -> PriceListValueType.DECIMAL;

            case PAGE_COUNT,
                 EXTERNAL_STOCK -> PriceListValueType.INTEGER;

            case PUBLICATION_DATE -> PriceListValueType.DATE;

            case COVER_URL -> PriceListValueType.URL;

            default -> PriceListValueType.TEXT;
        };
    }

    private boolean matchesAny(
            String value,
            String... candidates
    ) {
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}