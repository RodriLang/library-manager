package com.rodrilang.librarymanager.inventory.pricing.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class NormalizedPriceListStorageTest {

    @TempDir Path tempDir;

    @Test
    void gzipRoundtripPreservesCsvBytes() throws Exception {
        Path source = tempDir.resolve("source.csv");
        String csv = "isbn;titulo;precio\n9789500000000;Niño y océano;1234,56\n".repeat(2000);
        byte[] original = csv.getBytes(StandardCharsets.UTF_8);
        Files.write(source, original);

        Path compressed = NormalizedPriceListStorage.gzip(source);
        try {
            assertTrue(compressed.getFileName().toString().endsWith(".csv.gz"));
            assertTrue(Files.size(compressed) < Files.size(source));
            try (InputStream input = new GZIPInputStream(Files.newInputStream(compressed))) {
                assertArrayEquals(original, input.readAllBytes());
            }
        } finally {
            Files.deleteIfExists(compressed);
        }
    }

    @Test
    void cloudinaryLimitIsTenMebibytes() {
        assertEquals(10_485_760L, NormalizedPriceListStorage.MAX_RAW_UPLOAD_BYTES);
    }
}
