package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import org.apache.poi.util.XMLHelper;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Scans only the sample rows of a sheet for indices into sharedStrings.xml. */
final class PreviewSharedStringIndexCollector {
    private PreviewSharedStringIndexCollector() {}

    static Set<Integer> collect(InputStream sheet, int maxRows, int maxColumns) throws Exception {
        Set<Integer> indices = new HashSet<>();
        var parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new DefaultHandler() {
            private boolean sharedCell;
            private boolean inValue;
            private StringBuilder indexText;

            @Override public void startElement(String uri, String local, String qName, Attributes attributes) {
                String name = local.isEmpty() ? qName : local;
                if ("row".equals(name)) {
                    String row = attributes.getValue("r");
                    if (row != null && Integer.parseInt(row) > maxRows) throw new PreviewLimitReachedException();
                } else if ("c".equals(name)) {
                    String cell = attributes.getValue("r");
                    sharedCell = "s".equals(attributes.getValue("t")) && withinColumns(cell, maxColumns);
                } else if (sharedCell && "v".equals(name)) {
                    inValue = true;
                    indexText = new StringBuilder();
                }
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (inValue) indexText.append(ch, start, length);
            }
            @Override public void endElement(String uri, String local, String qName) {
                String name = local.isEmpty() ? qName : local;
                if ("v".equals(name) && inValue) {
                    String value = indexText.toString().trim();
                    if (!value.isEmpty()) indices.add(Integer.parseInt(value));
                    inValue = false;
                } else if ("c".equals(name)) sharedCell = false;
            }
        });
        try {
            parser.parse(new InputSource(sheet));
        } catch (PreviewLimitReachedException expected) {
            // Collected everything used by the sample rows.
        }
        return indices;
    }

    private static boolean withinColumns(String reference, int maxColumns) {
        if (reference == null) return true;
        int column = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = reference.charAt(i);
            if (c >= 'A' && c <= 'Z') column = column * 26 + c - 'A' + 1;
            else if (c >= 'a' && c <= 'z') column = column * 26 + c - 'a' + 1;
            else break;
        }
        return column > 0 && column <= maxColumns;
    }
}
