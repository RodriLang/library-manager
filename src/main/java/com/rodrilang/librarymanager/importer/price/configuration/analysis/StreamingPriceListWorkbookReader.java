package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.config.PriceListAnalysisProperties;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.internal.PriceListSheetPreview;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class StreamingPriceListWorkbookReader {

    private final PriceListAnalysisProperties properties;

    public List<PriceListSheetPreview> read(
            MultipartFile file
    ) {
        validateFile(file);

        try (
                InputStream inputStream = file.getInputStream();
                OPCPackage opcPackage =
                        OPCPackage.open(inputStream)
        ) {
            XSSFReader reader = new XSSFReader(opcPackage);

            StylesTable styles = reader.getStylesTable();

            ReadOnlySharedStringsTable sharedStrings =
                    new ReadOnlySharedStringsTable(opcPackage);

            DataFormatter formatter =
                    new DataFormatter(Locale.forLanguageTag("es-AR"));

            XSSFReader.SheetIterator sheetIterator =
                    (XSSFReader.SheetIterator)
                            reader.getSheetsData();

            List<PriceListSheetPreview> sheets =
                    new ArrayList<>();

            int sheetIndex = 0;

            while (
                    sheetIterator.hasNext()
                            && sheets.size() < properties.maxSheets()
            ) {
                try (InputStream sheetStream =
                             sheetIterator.next()) {

                    String sheetName =
                            sheetIterator.getSheetName();

                    sheets.add(
                            readSheet(
                                    sheetIndex,
                                    sheetName,
                                    sheetStream,
                                    styles,
                                    sharedStrings,
                                    formatter
                            )
                    );
                }

                sheetIndex++;
            }

            if (sheets.isEmpty()) {
                throw new BusinessException(
                        "El archivo Excel no contiene hojas analizables."
                );
            }

            return List.copyOf(sheets);

        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "No se pudo analizar el archivo Excel: "
                            + resolveExceptionMessage(exception)
            );
        }
    }

    private PriceListSheetPreview readSheet(
            int sheetIndex,
            String sheetName,
            InputStream sheetStream,
            StylesTable styles,
            ReadOnlySharedStringsTable sharedStrings,
            DataFormatter formatter
    ) throws Exception {

        PriceListSheetPreviewHandler previewHandler =
                new PriceListSheetPreviewHandler(
                        sheetIndex,
                        sheetName,
                        properties.maxPreviewRows(),
                        properties.maxColumns()
                );

        XSSFSheetXMLHandler contentHandler =
                new XSSFSheetXMLHandler(
                        styles,
                        null,
                        sharedStrings,
                        previewHandler,
                        formatter,
                        false
                );

        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(contentHandler);

        try {
            parser.parse(new InputSource(sheetStream));
        } catch (PreviewLimitReachedException ignored) {
            // Finalización esperada al obtener la vista previa.
        }

        return previewHandler.toResult();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    "Debe seleccionar un archivo Excel."
            );
        }

        if (
                file.getSize()
                        > properties.maxFileSize().toBytes()
        ) {
            throw new BusinessException(
                    "El archivo supera el tamaño máximo permitido de "
                            + properties.maxFileSize().toMegabytes()
                            + " MB."
            );
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(
                    "No se pudo determinar el nombre del archivo."
            );
        }

        String normalized =
                fileName.toLowerCase(Locale.ROOT);

        if (!normalized.endsWith(".xlsx")) {
            throw new BusinessException(
                    "El análisis automático requiere un archivo .xlsx."
            );
        }
    }

    private String resolveExceptionMessage(
            Exception exception
    ) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? "el archivo no posee un formato válido."
                : message;
    }
}