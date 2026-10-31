package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TiendanubePublicationContentSimilarityTest {
    @Test
    void flagsShortenedTitleWithAuthorVariation() {
        assertTrue(TiendanubePublicationContentService.similarPublicationNames(
                "los chicos malos alfredo lopez", "los chicos malos y otros cuentos alfredo g lopez"));
    }

    @Test
    void avoidsUnrelatedBooksWithOnlyOneSharedTerm() {
        assertFalse(TiendanubePublicationContentService.similarPublicationNames(
                "la casa de los espiritus", "la casa del bosque encantado"));
    }
}
