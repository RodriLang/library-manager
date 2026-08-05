package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import lombok.RequiredArgsConstructor;
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
public class StreamingConfigurablePriceListParserImpl implements StreamingConfigurablePriceListParser {

    private final ConfigurableStreamingRowMapper rowMapper;

    @Override
    public void parse(
            Path filePath,
            PriceListImportConfig config,
            Consumer<PriceListRow> rowConsumer
    ) {
        try (OPCPackage opcPackage =
                     OPCPackage.open(filePath.toFile())) {

            XSSFReader reader = new XSSFReader(opcPackage);

            StylesTable styles = reader.getStylesTable();

            ReadOnlySharedStringsTable sharedStrings =
                    new ReadOnlySharedStringsTable(opcPackage);

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

                    parseSheet(
                            sheetStream,
                            styles,
                            sharedStrings,
                            config,
                            rowConsumer
                    );

                    processed = true;
                    break;
                }
            }

            if (!processed) {
                throw new BusinessException(
                        "No se encontró la hoja configurada."
                );
            }

        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "No se pudo leer la lista de precios: "
                            + safeMessage(exception)
            );
        }
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