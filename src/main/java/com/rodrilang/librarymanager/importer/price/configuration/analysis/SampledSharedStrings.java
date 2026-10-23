package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.util.XMLHelper;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Only retains shared strings referenced by the bounded header preview. */
final class SampledSharedStrings implements SharedStrings {
    private final Map<Integer, RichTextString> values;

    private SampledSharedStrings(Map<Integer, RichTextString> values) {
        this.values = values;
    }

    static SampledSharedStrings read(InputStream stream, Set<Integer> indices) throws Exception {
        Map<Integer, RichTextString> values = new HashMap<>();
        if (stream == null || indices.isEmpty()) return new SampledSharedStrings(values);
        var parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new DefaultHandler() {
            private int index = -1;
            private boolean inItem;
            private boolean inText;
            private boolean inPhonetic;
            private StringBuilder text;

            @Override public void startElement(String uri, String local, String qName, Attributes attributes) {
                String name = local.isEmpty() ? qName : local;
                if ("si".equals(name)) {
                    index++;
                    inItem = true;
                    text = indices.contains(index) ? new StringBuilder() : null;
                } else if (inItem && "rPh".equals(name)) {
                    inPhonetic = true;
                } else if (inItem && "t".equals(name) && !inPhonetic) {
                    inText = true;
                }
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (inText && text != null) text.append(ch, start, length);
            }
            @Override public void endElement(String uri, String local, String qName) {
                String name = local.isEmpty() ? qName : local;
                if ("t".equals(name)) inText = false;
                if ("rPh".equals(name)) inPhonetic = false;
                if ("si".equals(name)) {
                    if (text != null) values.put(index, new XSSFRichTextString(text.toString()));
                    inItem = false;
                    text = null;
                }
            }
        });
        parser.parse(new InputSource(stream));
        return new SampledSharedStrings(values);
    }

    @Override public RichTextString getItemAt(int index) {
        RichTextString value = values.get(index);
        if (value == null) throw new IllegalArgumentException("Missing shared string in preview: " + index);
        return value;
    }
    @Override public int getCount() { return values.size(); }
    @Override public int getUniqueCount() { return values.size(); }
}
