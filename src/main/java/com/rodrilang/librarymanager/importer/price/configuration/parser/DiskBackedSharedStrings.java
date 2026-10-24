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
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams the XLSX shared-string table to a temporary random-access file.
 * Keeps only primitive offsets in heap, never a list of all strings.
 * Must be closed after the sheet parser finishes.
 */
final class DiskBackedSharedStrings implements SharedStrings, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DiskBackedSharedStrings.class);
    private static final int MAX_STRING_CHARS = 1_048_576;
    // Strict heap budget: cache only small strings, never the entire XLSX dictionary.
    private static final int CACHE_MAX_ENTRIES = Math.max(128,
            Math.min(4096, Integer.getInteger("anaquel.prices.sst-cache-entries", 1024)));
    private static final long CACHE_MAX_BYTES = 2L * 1024 * 1024;
    private static final int CACHE_MAX_VALUE_CHARS = 8192;
    private final Path path;
    private final RandomAccessFile file;
    private long[] offsets = new long[4096];
    private int count;
    private final LinkedHashMap<Integer, String> cache = new LinkedHashMap<>(128, .75f, true);
    // Reused by the single SAX parsing thread. Never retain one byte[] per SST entry.
    private byte[] readBuffer = new byte[256];
    private long cachedBytes;
    private long cacheHits;
    private long cacheMisses;
    private long diskReadNanos;
    private long diskReads;
    private long bytesRead;
    private long oversizedValues;
    private long evictions;

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

    @Override public RichTextString getItemAt(int index) {
        // Compatibility with POI readers. FAST_SAX calls getStringAt directly
        // and avoids allocating XSSFRichTextString for every selected cell.
        return new XSSFRichTextString(getStringAt(index));
    }

    synchronized String getStringAt(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("Shared string index " + index);
        String cached = cache.get(index);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        long started = System.nanoTime();
        try {
            file.seek(offsets[index]);
            int length = file.readInt();
            if (length < 0 || length > MAX_STRING_CHARS * 4) throw new IOException("Invalid shared string length");
            if (readBuffer.length < length) {
                int capacity = Math.max(length, Math.min(MAX_STRING_CHARS * 4, Math.max(256, readBuffer.length * 2)));
                readBuffer = new byte[capacity];
            }
            file.readFully(readBuffer, 0, length);
            String value = new String(readBuffer, 0, length, StandardCharsets.UTF_8);
            diskReads++;
            bytesRead += length;
            if (value.length() <= CACHE_MAX_VALUE_CHARS) {
                long weight = (long) value.length() * 2L;
                while (!cache.isEmpty() && (cache.size() >= CACHE_MAX_ENTRIES || cachedBytes + weight > CACHE_MAX_BYTES)) {
                    Map.Entry<Integer, String> eldest = cache.entrySet().iterator().next();
                    cachedBytes -= (long) eldest.getValue().length() * 2L;
                    cache.remove(eldest.getKey());
                    evictions++;
                }
                if (weight <= CACHE_MAX_BYTES) {
                    cache.put(index, value);
                    cachedBytes += weight;
                }
            } else {
                oversizedValues++;
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer una cadena del Excel.", exception);
        } finally {
            diskReadNanos += System.nanoTime() - started;
        }
    }

    synchronized Stats stats() {
        return new Stats(cacheHits, cacheMisses, diskReads, bytesRead,
                diskReadNanos / 1_000_000L, cache.size(), cachedBytes, evictions, oversizedValues);
    }

    record Stats(long hits, long misses, long diskReads, long bytesRead,
                 long diskReadMs, int entries, long cacheBytes, long evictions, long oversizedValues) {
        long hitPercent() {
            return hits + misses == 0 ? 0 : (100 * hits / (hits + misses));
        }
    }

    @Override public int getCount() { return count; }
    @Override public int getUniqueCount() { return count; }

    @Override public void close() throws IOException {
        Stats result = stats();
        log.info("PRICE LIST PARSER SST STATS entries={} cacheHits={} cacheMisses={} hitPercent={} diskReads={} bytesRead={} diskReadMs={} cacheEntries={} cacheBytes={} evictions={} oversizedValues={}",
                count, result.hits(), result.misses(), result.hitPercent(), result.diskReads(), result.bytesRead(),
                result.diskReadMs(), result.entries(), result.cacheBytes(), result.evictions(), result.oversizedValues());
        try { file.close(); } finally { Files.deleteIfExists(path); }
    }
}
