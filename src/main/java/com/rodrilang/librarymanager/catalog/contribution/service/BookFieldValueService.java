package com.rodrilang.librarymanager.catalog.contribution.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.service.PublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookFieldValueService {

    private final ObjectMapper objectMapper;
    private final PublisherService publisherService;
    private final AuthorService authorService;

    public boolean hasValue(Book book, BookField field) {
        return switch (field) {
            case TITLE -> hasText(book.getTitle());
            case SUBTITLE -> hasText(book.getSubtitle());
            case DESCRIPTION -> hasText(book.getDescription());
            case LANGUAGE -> hasText(book.getLanguage());
            case PAGE_COUNT -> book.getPageCount() != null;
            case PUBLICATION_YEAR -> book.getPublicationYear() != null;
            case PUBLICATION_MONTH -> book.getPublicationMonth() != null;
            case COVER_URL -> hasText(book.getCoverUrl());
            case CATEGORY_NAME -> hasText(book.getCategoryName());
            case GENRE_NAME -> hasText(book.getGenreName());
            case PUBLISHER -> book.getPublisher() != null;
            case AUTHORS -> book.getAuthors() != null && !book.getAuthors().isEmpty();
            case WEIGHT_GRAMS -> book.getWeightGrams() != null;
            case WIDTH_CM -> book.getWidthCm() != null;
            case HEIGHT_CM -> book.getHeightCm() != null;
            case DEPTH_CM -> book.getDepthCm() != null;
        };
    }

    public String serializeCurrent(Book book, BookField field) {
        return switch (field) {
            case TITLE -> normalize(book.getTitle());
            case SUBTITLE -> normalize(book.getSubtitle());
            case DESCRIPTION -> normalize(book.getDescription());
            case LANGUAGE -> normalize(book.getLanguage());
            case PAGE_COUNT -> string(book.getPageCount());
            case PUBLICATION_YEAR -> string(book.getPublicationYear());
            case PUBLICATION_MONTH -> string(book.getPublicationMonth());
            case COVER_URL -> normalize(book.getCoverUrl());
            case CATEGORY_NAME -> normalize(book.getCategoryName());
            case GENRE_NAME -> normalize(book.getGenreName());
            case PUBLISHER -> book.getPublisher() == null ? null : String.valueOf(book.getPublisher().getId());
            case AUTHORS -> serializeAuthorIds(book.getAuthors());
            case WEIGHT_GRAMS -> decimal(book.getWeightGrams());
            case WIDTH_CM -> decimal(book.getWidthCm());
            case HEIGHT_CM -> decimal(book.getHeightCm());
            case DEPTH_CM -> decimal(book.getDepthCm());
        };
    }

    public String serializeProposed(BookField field, Object value) {
        if (value == null) {
            return null;
        }
        return switch (field) {
            case AUTHORS -> writeJson(new TreeSet<>((Set<Long>) value));
            case TITLE, SUBTITLE, DESCRIPTION, LANGUAGE, COVER_URL, CATEGORY_NAME, GENRE_NAME -> normalize((String) value);
            case PAGE_COUNT, PUBLICATION_YEAR, PUBLICATION_MONTH, PUBLISHER -> String.valueOf(value);
            case WEIGHT_GRAMS, WIDTH_CM, HEIGHT_CM, DEPTH_CM -> decimal((BigDecimal) value);
        };
    }

    public String displaySerialized(BookField field, String value) {
        if (value == null) {
            return null;
        }
        try {
            return switch (field) {
                case PUBLISHER -> publisherService.getEntityById(Long.valueOf(value)).getName();
                case AUTHORS -> authorService.getEntitiesByIds(readAuthorIds(value)).stream()
                        .sorted(Comparator.comparing(Author::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(Author::getName)
                        .collect(Collectors.joining(", "));
                default -> value;
            };
        } catch (RuntimeException ex) {
            return value;
        }
    }

    public boolean equivalent(Book book, BookField field, String proposedValue) {
        String current = serializeCurrent(book, field);
        if (current == null) {
            return proposedValue == null;
        }
        return current.equals(proposedValue);
    }

    public void clear(Book book, BookField field) {
        switch (field) {
            case TITLE -> throw new BusinessException("El título no puede quedar vacío.");
            case SUBTITLE -> book.setSubtitle(null);
            case DESCRIPTION -> book.setDescription(null);
            case LANGUAGE -> book.setLanguage(null);
            case PAGE_COUNT -> book.setPageCount(null);
            case PUBLICATION_YEAR -> {
                book.setPublicationYear(null);
                book.setPublicationMonth(null);
                book.setFieldSource(BookField.PUBLICATION_MONTH, null);
            }
            case PUBLICATION_MONTH -> book.setPublicationMonth(null);
            case COVER_URL -> book.clearCover();
            case CATEGORY_NAME -> book.setCategoryName(null);
            case GENRE_NAME -> book.setGenreName(null);
            case PUBLISHER -> book.setPublisher(null);
            case AUTHORS -> book.setAuthors(new java.util.HashSet<>());
            case WEIGHT_GRAMS -> book.setWeightGrams(null);
            case WIDTH_CM -> book.setWidthCm(null);
            case HEIGHT_CM -> book.setHeightCm(null);
            case DEPTH_CM -> book.setDepthCm(null);
        }
    }

    public void applySerialized(Book book, BookField field, String value) {
        try {
            switch (field) {
                case TITLE -> book.setTitle(value);
                case SUBTITLE -> book.setSubtitle(value);
                case DESCRIPTION -> book.setDescription(value);
                case LANGUAGE -> book.setLanguage(value);
                case PAGE_COUNT -> book.setPageCount(Integer.valueOf(value));
                case PUBLICATION_YEAR -> book.setPublicationYear(Integer.valueOf(value));
                case PUBLICATION_MONTH -> book.setPublicationMonth(Integer.valueOf(value));
                case COVER_URL -> book.updateCover(value, "BOOKSTORE_CONTRIBUTION");
                case CATEGORY_NAME -> book.setCategoryName(value);
                case GENRE_NAME -> book.setGenreName(value);
                case PUBLISHER -> book.setPublisher(publisherService.getEntityById(Long.valueOf(value)));
                case AUTHORS -> book.setAuthors(authorService.getEntitiesByIds(readAuthorIds(value)));
                case WEIGHT_GRAMS -> book.setWeightGrams(new BigDecimal(value));
                case WIDTH_CM -> book.setWidthCm(new BigDecimal(value));
                case HEIGHT_CM -> book.setHeightCm(new BigDecimal(value));
                case DEPTH_CM -> book.setDepthCm(new BigDecimal(value));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException("El valor propuesto para " + field + " no es válido.");
        }
    }

    private String serializeAuthorIds(Set<Author> authors) {
        if (authors == null || authors.isEmpty()) {
            return null;
        }
        TreeSet<Long> ids = new TreeSet<>();
        authors.stream().sorted(Comparator.comparing(Author::getId)).forEach(author -> ids.add(author.getId()));
        return writeJson(ids);
    }

    private Set<Long> readAuthorIds(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Set<Long>>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException("La propuesta de autores no tiene un formato válido.");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("No se pudo procesar el valor bibliográfico.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
