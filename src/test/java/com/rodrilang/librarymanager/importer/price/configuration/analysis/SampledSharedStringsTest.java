package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SampledSharedStringsTest {
    @Test
    void retainsOnlyReferencedEntriesIncludingRichTextAndEscapedValues() throws Exception {
        String xml = """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
                  <si><t>Unused</t></si>
                  <si><r><t>La </t></r><r><t>casa</t></r></si>
                  <si><t>Libro &amp; precio</t></si>
                </sst>
                """;
        var result = SampledSharedStrings.read(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Set.of(1, 2));
        assertEquals("La casa", result.getItemAt(1).getString());
        assertEquals("Libro & precio", result.getItemAt(2).getString());
        assertThrows(IllegalArgumentException.class, () -> result.getItemAt(0));
    }

    @Test
    void scansOnlyPreviewRowsAndColumns() throws Exception {
        String sheet = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                  <row r="1"><c r="A1" t="s"><v>17</v></c><c r="C1" t="s"><v>20</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>19</v></c></row>
                </sheetData></worksheet>
                """;
        var indices = PreviewSharedStringIndexCollector.collect(
                new ByteArrayInputStream(sheet.getBytes(StandardCharsets.UTF_8)), 1, 2);
        assertEquals(Set.of(17), indices);
    }
}
