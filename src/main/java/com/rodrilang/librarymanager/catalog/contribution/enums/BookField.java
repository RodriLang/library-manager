package com.rodrilang.librarymanager.catalog.contribution.enums;

public enum BookField {
    TITLE("title"),
    SUBTITLE("subtitle"),
    DESCRIPTION("description"),
    LANGUAGE("language"),
    PAGE_COUNT("pageCount"),
    PUBLICATION_YEAR("publicationYear"),
    PUBLICATION_MONTH("publicationMonth"),
    COVER_URL("coverUrl"),
    CATEGORY_NAME("categoryName"),
    GENRE_NAME("genreName"),
    PUBLISHER("publisher"),
    AUTHORS("authors"),
    WEIGHT_GRAMS("weightGrams"),
    WIDTH_CM("widthCm"),
    HEIGHT_CM("heightCm"),
    DEPTH_CM("depthCm");

    private final String key;

    BookField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
