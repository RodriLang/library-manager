package com.rodrilang.librarymanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextNormalizerTest {

    @Test
    void tokenPrefixSearchShouldIgnorePunctuationAndTokenOrderAtQueryLevel() {
        assertEquals(
                "ledesma:* & ivan:*",
                TextNormalizer.normalizeForTokenPrefixSearch("LEDESMA, IVAN")
        );

        assertEquals(
                "ivan:* & ledesma:*",
                TextNormalizer.normalizeForTokenPrefixSearch("IVAN LEDESMA")
        );
    }

    @Test
    void tokenPrefixSearchShouldPrefixEveryToken() {
        assertEquals(
                "iva:* & ledes:*",
                TextNormalizer.normalizeForTokenPrefixSearch("IVA LEDES")
        );
    }
}
