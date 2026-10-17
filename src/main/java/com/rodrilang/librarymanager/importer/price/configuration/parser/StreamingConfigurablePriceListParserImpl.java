package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamingConfigurablePriceListParserImpl implements StreamingConfigurablePriceListParser {

    private final ConfigurableStreamingRowMapper rowMapper;

    @Override
    public void parse(
            Path filePath,
            PriceListImportConfig config,
            Consumer<PriceListRow> rowConsumer
    ) {
        long overallStarted = System.nanoTime();
        log.info("PRICE LIST PARSER OPEN START fileBytes={} sheetStrategy={} firstDataRow={} mappings={}",
                safeFileSize(filePath), config.getSheetStrategy(), config.getFirstDataRowIndex(),
                config.getMappings() == null ? 0 : config.getMappings().size());
        try (OPCPackage opcPackage =
                     OPCPackage.open(filePath.toFile())) {
            log.info("PRICE LIST PARSER OPEN DONE timeMs={}", millisSince(overallStarted));
            long setupStarted = System.nanoTime();

            XSSFReader reader = new XSSFReader(opcPackage);

            StylesTable styles = reader.getStylesTable();

            ReadOnlySharedStringsTable sharedStrings =
                    new ReadOnlySharedStringsTable(opcPackage);

            log.info("PRICE LIST PARSER SETUP DONE timeMs={}", millisSince(setupStarted));

            XSSFReader.SheetIterator iterator =
                    (XSSFReader.SheetIterator)
                            reader.getSheetsData();

            int sheetIndex = 0;
            boolean processed = false;

            while (iterator.hasNext()) {
                try (InputStream sheetStream = iterator.next()) {
                    String sheetName = iterator.getSheetName();

                    if (!matchesSheet(
                            config,
                            sheetIndex,
                            sheetName
                    )) {
                        sheetIndex++;
                        continue;
                    }

                    boolean fastPath = canUseFastPath(config);
                    long parseStarted = System.nanoTime();
                    log.info("PRICE LIST PARSER START mode={} sheet={} sheetIndex={} setupElapsedMs={}",
                            fastPath ? "FAST_SAX" : "FORMATTED", sheetName, sheetIndex, millisSince(overallStarted));
                    if (fastPath) {
                        parseSheetFast(
                                sheetStream,
                                sharedStrings,
                                config,
                                rowConsumer
                        );
                    } else {
                        parseSheet(
                                sheetStream,
                                styles,
                                sharedStrings,
                                config,
                                rowConsumer
                        );
                    }
                    log.info(
                            "PRICE LIST PARSER mode={} sheet={} timeMs={}",
                            fastPath ? "FAST_SAX" : "FORMATTED",
                            sheetName,
                            (System.nanoTime() - parseStarted) / 1_000_000L
                    );

                    processed = true;
                    break;
                }
            }

            log.info("PRICE LIST PARSER FINISHED totalMs={} processed={}", millisSince(overallStarted), processed);
            if (!processed) {
                throw new BusinessException(
                        "No se encontró la hoja configurada."
                );
            }

        } catch (BusinessException exception) {
            log.warn("PRICE LIST PARSER FAILED totalMs={} errorType={}", millisSince(overallStarted),
                    exception.getClass().getSimpleName());
            throw exception;
        } catch (Exception exception) {
            log.warn("PRICE LIST PARSER FAILED totalMs={} errorType={}", millisSince(overallStarted),
                    exception.getClass().getSimpleName(), exception);
            throw new BusinessException(
                    "No se pudo leer la lista de precios: "
                            + safeMessage(exception)
            );
        }
    }


    private static long millisSince(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static long safeFileSize(Path file) {
        try {
            return java.nio.file.Files.size(file);
        } catch (java.io.IOException ignored) {
            return -1L;
        }
    }

    private void parseSheetFast(
            InputStream sheetStream,
            ReadOnlySharedStringsTable sharedStrings,
            PriceListImportConfig config,
            Consumer<PriceListRow> consumer
    ) throws Exception {
        FastMappedPriceListSheetHandler handler =
                new FastMappedPriceListSheetHandler(
                        config,
                        rowMapper,
                        consumer,
                        sharedStrings
                );

        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(handler);
        parser.parse(new InputSource(sheetStream));
    }

    private boolean canUseFastPath(PriceListImportConfig config) {
        if (config.getMappings() == null || config.getMappings().isEmpty()) {
            return false;
        }

        for (PriceListColumnMapping mapping : config.getMappings()) {
            if (!mapping.isActive()) {
                continue;
            }
            if (mapping.getColumnIndex() == null || mapping.getColumnIndex() < 0) {
                return false;
            }
            // Numeric Excel dates depend on cell style information. Keep those
            // formats on XSSFSheetXMLHandler/DataFormatter for exact compatibility.
            if (mapping.getValueType() == PriceListValueType.DATE) {
                return false;
            }
        }
        return true;
    }

    private void parseSheet(
            InputStream sheetStream,
            StylesTable styles,
            ReadOnlySharedStringsTable sharedStrings,
            PriceListImportConfig config,
            Consumer<PriceListRow> consumer
    ) throws Exception {

        StreamingPriceListSheetHandler handler =
                new StreamingPriceListSheetHandler(
                        config,
                        rowMapper,
                        consumer
                );

        XSSFSheetXMLHandler contentHandler =
                new XSSFSheetXMLHandler(
                        styles,
                        null,
                        sharedStrings,
                        handler,
                        new StreamingExcelDataFormatter(
                                Locale.forLanguageTag("es-AR")
                        ),
                        false
                );

        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(contentHandler);
        parser.parse(new InputSource(sheetStream));
    }

    private boolean matchesSheet(
            PriceListImportConfig config,
            int sheetIndex,
            String sheetName
    ) {
        return switch (config.getSheetStrategy()) {

            case BY_INDEX -> config.getSheetIndex() != null
                    && sheetIndex == config.getSheetIndex();

            case BY_NAME -> hasConfiguredName(config)
                    && sheetName.equalsIgnoreCase(config.getSheetName().trim());

            case NAME_CONTAINS -> hasConfiguredName(config)
                    && sheetName.toLowerCase(Locale.ROOT)
                    .contains(config.getSheetName().trim().toLowerCase(Locale.ROOT));
        };
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? "formato Excel inválido."
                : exception.getMessage();
    }

    private boolean hasConfiguredName(PriceListImportConfig config) {
        return config.getSheetName() != null
                && !config.getSheetName().isBlank();
    }
}