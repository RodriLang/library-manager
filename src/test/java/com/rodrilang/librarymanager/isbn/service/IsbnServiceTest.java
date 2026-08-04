package com.rodrilang.librarymanager.isbn.service;

import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IsbnServiceTest {

    private IsbnService isbnService;

    @BeforeEach
    void setUp() {
        isbnService = new IsbnService();
    }

    @Test
    void shouldParseValidIsbn13() {
        ParsedIsbn result = isbnService.parse("9789877911022");

        assertTrue(result.valid());
        assertNull(result.isbn10());
        assertEquals("9789877911022", result.isbn13());
        assertEquals("9789877911022", result.preferredIsbn());
    }

    @Test
    void shouldParseAndConvertValidIsbn10() {
        ParsedIsbn result = isbnService.parse("0-306-40615-2");

        assertTrue(result.valid());
        assertEquals("0306406152", result.isbn10());
        assertEquals("9780306406157", result.isbn13());
        assertEquals("9780306406157", result.preferredIsbn());
    }

    @Test
    void shouldSupportXAsIsbn10CheckDigit() {
        ParsedIsbn result = isbnService.parse("080442957X");

        assertTrue(result.valid());
        assertEquals("080442957X", result.isbn10());
        assertEquals("9780804429573", result.isbn13());
    }

    @Test
    void shouldRejectInvalidIsbn() {
        ParsedIsbn result = isbnService.parse("123456");

        assertFalse(result.valid());
        assertNull(result.isbn10());
        assertNull(result.isbn13());
    }

    @Test
    void shouldNormalizeSpacesAndHyphens() {
        assertEquals("9780306406157", isbnService.normalize("978-0-306-40615-7"));
    }
}