package com.rodrilang.librarymanager.importer.price.configuration.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DiskBackedSharedStringsTest {
    @Test
    void resolvesPlainAndRichTextAndIgnoresPhoneticRuns() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
                  <si><t>Árbol &amp; río</t></si>
                  <si><r><t>Buenos </t></r><r><t>Aires</t></r></si>
                  <si><t>Visible</t><rPh><t>invisible</t></rPh></si>
                </sst>
                """;
        try (var index = DiskBackedSharedStrings.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            assertEquals(3, index.getUniqueCount());
            assertEquals("Árbol & río", index.getItemAt(0).getString());
            assertEquals("Buenos Aires", index.getItemAt(1).getString());
            assertEquals("Visible", index.getItemAt(2).getString());
            assertThrows(IndexOutOfBoundsException.class, () -> index.getItemAt(3));
        }
    }
}
