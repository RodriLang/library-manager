package com.rodrilang.librarymanager.importer.price.configuration.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DiskBackedSharedStringsTest {
    @Test
    void boundedCacheTracksHitsMissesAndEvictions() throws Exception {
        StringBuilder xml = new StringBuilder("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (int i = 0; i < 1100; i++) xml.append("<si><t>Value_").append(i).append("</t></si>");
        xml.append("</sst>");
        try (var index = DiskBackedSharedStrings.load(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)))) {
            assertEquals("Value_0", index.getItemAt(0).getString());
            assertEquals("Value_0", index.getItemAt(0).getString());
            for (int i = 1; i < 1100; i++) assertEquals("Value_" + i, index.getItemAt(i).getString());
            var stats = index.stats();
            assertEquals(1, stats.hits());
            assertEquals(1100, stats.misses());
            assertEquals(1100, stats.diskReads());
            assertTrue(stats.entries() <= 1024);
            assertTrue(stats.cacheBytes() <= 2 * 1024 * 1024);
            assertTrue(stats.evictions() > 0);
        }
    }

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
