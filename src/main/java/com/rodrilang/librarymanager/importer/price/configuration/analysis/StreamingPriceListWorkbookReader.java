package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.config.PriceListAnalysisProperties;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.internal.PriceListSheetPreview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import com.rodrilang.librarymanager.importer.price.configuration.parser.LegacyXlsSupport;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingExcelDataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamingPriceListWorkbookReader {

    private final PriceListAnalysisProperties properties;
    private final PriceListAnalysisFileStorage fileStorage;

    public List<PriceListSheetPreview> read(
            MultipartFile file
    ) {
        validateFile(file);

        Path temporaryFile = fileStorage.store(file);

        try {
            return LegacyXlsSupport.isXls(temporaryFile)
                    ? readXls(temporaryFile) : readFromPath(temporaryFile);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo identificar el formato del archivo Excel", e
            );
        } finally {
            fileStorage.deleteQuietly(temporaryFile);
        }
    }

    private List<PriceListSheetPreview> readXls(Path filePath) {
        try (HSSFWorkbook workbook = LegacyXlsSupport.open(filePath)) {
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-AR"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<PriceListSheetPreview> previews = new ArrayList<>();
            int sheetCount = Math.min(workbook.getNumberOfSheets(), properties.maxSheets());
            for (int index = 0; index < sheetCount; index++) {
                Sheet sheet = workbook.getSheetAt(index);
                List<PriceListPreviewRowResponse> rows = new ArrayList<>();
                int columns = 0;
                int rowLimit = properties.maxPreviewRows();
                for (int rowIndex = 0; rowIndex < rowLimit; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) continue;
                    List<String> cells = LegacyXlsSupport.cells(row, properties.maxColumns(), formatter, evaluator);
                    columns = Math.max(columns, cells.size());
                    rows.add(new PriceListPreviewRowResponse(rowIndex, cells));
                }
                List<PriceListPreviewRowResponse> normalized = new ArrayList<>();
                for (PriceListPreviewRowResponse row : rows) {
                    List<String> values = new ArrayList<>(row.cells());
                    while (values.size() < columns) values.add("");
                    normalized.add(new PriceListPreviewRowResponse(row.rowIndex(), List.copyOf(values)));
                }
                previews.add(new PriceListSheetPreview(index, sheet.getSheetName(),
                        normalized.size(), columns, sheet.getLastRowNum() >= rowLimit, List.copyOf(normalized)));
            }
            if (previews.isEmpty()) throw new BusinessException("El archivo Excel no contiene hojas analizables.");
            return List.copyOf(previews);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("No se pudo analizar el archivo XLS: " + resolveExceptionMessage(exception));
        }
    }

    private List<PriceListSheetPreview> readFromPath(
            Path filePath
    ) {
        try (
                OPCPackage opcPackage =
                        OPCPackage.open(
                                filePath.toFile(),
                                PackageAccess.READ
                        )
        ) {
            log.info("PRICE LIST ANALYSIS ENGINE=SELECTIVE_SST_V2 fileBytes={} maxSheets={} maxPreviewRows={} maxColumns={}",
                    java.nio.file.Files.size(filePath), properties.maxSheets(),
                    properties.maxPreviewRows(), properties.maxColumns());

            XSSFReader reader =
                    new XSSFReader(opcPackage);

            StylesTable styles =
                    reader.getStylesTable();

            // Two bounded sheet passes: find only the shared-string indices used by
            // preview rows, then materialize only those strings. A full shared
            // strings table can exhaust the heap for large supplier XLSX files.
            Set<Integer> neededStrings = new HashSet<>();
            XSSFReader.SheetIterator sampleIterator =
                    (XSSFReader.SheetIterator) reader.getSheetsData();
            int sampledSheets = 0;
            while (sampleIterator.hasNext() && sampledSheets < properties.maxSheets()) {
                try (InputStream sheet = sampleIterator.next()) {
                    neededStrings.addAll(PreviewSharedStringIndexCollector.collect(
                            sheet, properties.maxPreviewRows(), properties.maxColumns()));
                }
                sampledSheets++;
            }
            log.info("PRICE LIST ANALYSIS INDEX COLLECTED sampledSheets={} referencedIndices={}",
                    sampledSheets, neededStrings.size());
            SharedStrings sharedStrings;
            try (InputStream stringsStream = reader.getSharedStringsData()) {
                sharedStrings = SampledSharedStrings.read(stringsStream, neededStrings);
            }
            log.info("PRICE LIST ANALYSIS SELECTIVE SST READY sampledSheets={} requiredStrings={}",
                    sampledSheets, neededStrings.size());

            DataFormatter formatter =
                    new StreamingExcelDataFormatter(
                            Locale.forLanguageTag("es-AR")
                    );

            XSSFReader.SheetIterator sheetIterator =
                    (XSSFReader.SheetIterator)
                            reader.getSheetsData();

            List<PriceListSheetPreview> sheets =
                    new ArrayList<>();

            int sheetIndex = 0;

            while (
                    sheetIterator.hasNext()
                            && sheets.size()
                            < properties.maxSheets()
            ) {
                try (
                        InputStream sheetStream =
                                sheetIterator.next()
                ) {
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
            SharedStrings sharedStrings,
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

        XMLReader parser =
                XMLHelper.newXMLReader();

        parser.setContentHandler(contentHandler);

        try {
            parser.parse(
                    new InputSource(sheetStream)
            );
        } catch (
                PreviewLimitReachedException ignored
        ) {
            // Finalización esperada.
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

        if (!normalized.endsWith(".xlsx") && !normalized.endsWith(".xls")) {
            throw new BusinessException(
                    "El análisis automático requiere un archivo .xls o .xlsx."
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