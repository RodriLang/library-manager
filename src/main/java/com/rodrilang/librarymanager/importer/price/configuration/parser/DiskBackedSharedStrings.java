package com.rodrilang.librarymanager.importer.price.configuration.parser;

import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Streams the XLSX shared-string table to a temporary random-access file.
 * Keeps only primitive offsets in heap, never a list of all strings.
 * Must be closed after the sheet parser finishes.
 */
final class DiskBackedSharedStrings implements SharedStrings, AutoCloseable {
    private static final int MAX_STRING_CHARS = 1_048_576;
    private final Path path;
    private final RandomAccessFile file;
    private long[] offsets = new long[4096];
    private int count;

    private DiskBackedSharedStrings(Path path, RandomAccessFile file) {
        this.path = path;
        this.file = file;
    }

    static DiskBackedSharedStrings load(InputStream input) throws Exception {
        Path path = Files.createTempFile("anaquel-price-sst-", ".bin");
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(path.toFile(), "rw");
            DiskBackedSharedStrings table = new DiskBackedSharedStrings(path, file);
            if (input != null) {
                var parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new DefaultHandler() {
                    private StringBuilder value;
                    private boolean inText;
                    private boolean inPhonetic;

                    @Override public void startElement(String uri, String local, String qName, Attributes attributes) {
                        String name = local.isEmpty() ? qName : local;
                        if ("si".equals(name)) value = new StringBuilder(64);
                        else if (value != null && "rPh".equals(name)) inPhonetic = true;
                        else if (value != null && "t".equals(name) && !inPhonetic) inText = true;
                    }
                    @Override public void characters(char[] chars, int start, int length) throws SAXException {
                        if (inText && value != null) {
                            if ((long) value.length() + length > MAX_STRING_CHARS) {
                                throw new SAXException("La lista contiene una celda de texto demasiado extensa.");
                            }
                            value.append(chars, start, length);
                        }
                    }
                    @Override public void endElement(String uri, String local, String qName) throws SAXException {
                        String name = local.isEmpty() ? qName : local;
                        if ("t".equals(name)) inText = false;
                        else if ("rPh".equals(name)) inPhonetic = false;
                        else if ("si".equals(name)) {
                            try { table.append(value == null ? "" : value.toString()); }
                            catch (IOException ex) { throw new SAXException(ex); }
                            value = null;
                            inText = false;
                            inPhonetic = false;
                        }
                    }
                });
                parser.parse(new InputSource(input));
            }
            return table;
        } catch (Exception | Error ex) {
            if (file != null) try { file.close(); } catch (IOException ignored) { }
            try { Files.deleteIfExists(path); } catch (IOException cleanupFailure) { ex.addSuppressed(cleanupFailure); }
            throw ex;
        }
    }

    private void append(String value) throws IOException {
        if (count == offsets.length) offsets = Arrays.copyOf(offsets, Math.multiplyExact(offsets.length, 2));
        offsets[count++] = file.getFilePointer();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        file.writeInt(bytes.length);
        file.write(bytes);
    }

    @Override public synchronized RichTextString getItemAt(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("Shared string index " + index);
        try {
            file.seek(offsets[index]);
            int length = file.readInt();
            if (length < 0 || length > MAX_STRING_CHARS * 4) throw new IOException("Invalid shared string length");
            byte[] bytes = new byte[length];
            file.readFully(bytes);
            return new XSSFRichTextString(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer una cadena del Excel.", exception);
        }
    }

    @Override public int getCount() { return count; }
    @Override public int getUniqueCount() { return count; }

    @Override public void close() throws IOException {
        try { file.close(); } finally { Files.deleteIfExists(path); }
    }
}
