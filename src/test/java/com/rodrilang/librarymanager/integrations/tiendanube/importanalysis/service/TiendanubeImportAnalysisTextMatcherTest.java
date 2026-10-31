package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class TiendanubeImportAnalysisTextMatcherTest {
    private final TiendanubeImportAnalysisTextMatcher matcher = new TiendanubeImportAnalysisTextMatcher(null);

    private Object call(String method, Class<?>[] types, Object... args) throws Exception {
        Method m = TiendanubeImportAnalysisTextMatcher.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(matcher, args);
    }

    private double title(String remote, String catalog) throws Exception {
        String normalized = com.rodrilang.librarymanager.util.TextNormalizer.normalizeForSearch(remote);
        return (double) call("titleScore", new Class<?>[]{String.class, String.class}, normalized, catalog);
    }

    @Test void surnameInRemoteAuthorMustNotBeConsideredAsBookTitle() throws Exception {
        String name = "De lonja y callejón - Gonzalo Messi";
        String remoteTitle = (String) call("remoteTitle", new Class<?>[]{String.class}, name);
        assertEquals("De lonja y callejón", remoteTitle);
        assertEquals(0, title(remoteTitle, "MESSI"));
    }

    @Test void authorFirstNameMustNotFindMartinFierro() throws Exception {
        String title = (String) call("remoteTitle", new Class<?>[]{String.class}, "Yoeta - Martín Pucheta");
        assertEquals(0, title(title, "MARTIN FIERRO"));
    }

    @Test void exactTitlesArePreservedEvenWhenShort() throws Exception {
        assertEquals(1, title("Amiga", "AMIGA"));
        assertEquals(1, title("Carsick", "CARSICK"));
    }

    @Test void partialCommonWordRequiresAuthorEvidence() throws Exception {
        double score = title("Rezos a la Nada", "NADA DE NADA");
        assertFalse((boolean) call("isPlausible", new Class<?>[]{double.class, double.class}, score, 0d));
        double extended = title("El lado oscuro del sistema", "El lado oscuro");
        assertFalse((boolean) call("isPlausible", new Class<?>[]{double.class, double.class}, extended, 0d));
    }

    @Test void exactTitleWithDifferentAuthorCanStillBeReviewed() throws Exception {
        assertTrue((boolean) call("isPlausible", new Class<?>[]{double.class, double.class}, title("Intemperie", "INTEMPERIE"), 0d));
    }
}
