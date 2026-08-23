package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.util.ExcelCellValueReader;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigurablePriceListParserTest {

    private ConfigurablePriceListParser parser;

    @BeforeEach
    void setUp() {

        ExcelCellValueReader cellValueReader =
                new ExcelCellValueReader();

        PriceListSheetResolver sheetResolver =
                new PriceListSheetResolver();

        ConfigurablePriceListTemplateValidator templateValidator =
                new ConfigurablePriceListTemplateValidator(
                        cellValueReader
                );

        PriceListCellValueConverter converter =
                new PriceListCellValueConverter(
                        cellValueReader
                );

        ConfigurablePriceListRowMapper rowMapper =
                new ConfigurablePriceListRowMapper(
                        converter
                );

        ConfigurablePriceListRowInspector rowInspector =
                new ConfigurablePriceListRowInspector(
                        cellValueReader
                );

        parser = new ConfigurablePriceListParser(
                sheetResolver,
                templateValidator,
                rowMapper,
                rowInspector
        );
    }

    @Test
    void shouldParseLuongoPriceList() throws Exception {

        try (
                InputStream inputStream = getClass().getResourceAsStream("/price-lists/luongo.xlsx");
                Workbook workbook = WorkbookFactory.create(inputStream)
        ) {

            PriceListImportConfig config = createLuongoConfig();

            List<PriceListRow> rows = parser.parse(workbook, config);

            assertFalse(rows.isEmpty());

            PriceListRow first = rows.getFirst();

            assertEquals(2, first.rowNumber());
            assertEquals("9789877911022", first.isbn());
            assertEquals("CANCION DE LAS PREGUNTAS       RUST", first.title());
            assertEquals("Tallon Sebastia", first.authorName());
            assertEquals(0, new BigDecimal("28300").compareTo(first.retailPrice()));
            assertNotNull(first.metadata());
            assertNull(first.metadata().collectionName());
            PriceListRow withCollection = rows.stream()
                    .filter(row -> "EL ARTE DEL SABER LIGERO".equals(row.title()))
                    .findFirst()
                    .orElseThrow();

            assertNotNull(withCollection.metadata());
            assertEquals("BIBLIOTECA DE ENSAYO", withCollection.metadata().collectionName());
        }
    }

    @Test
    void shouldParseCarbonoPriceList() throws Exception {
        try (
                InputStream inputStream =
                        getClass().getResourceAsStream("/price-lists/carbono.xlsx");
                Workbook workbook =
                        WorkbookFactory.create(inputStream)
        ) {
            assertNotNull(inputStream);

            PriceListImportConfig config = createCarbonoConfig();

            List<PriceListRow> rows = parser.parse(workbook, config);

            assertFalse(rows.isEmpty());

            PriceListRow firstBook = rows.stream()
                    .filter(row -> "1917".equals(row.title()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("9789874086303", firstBook.isbn());
            assertEquals("Martín Kohan", firstBook.authorName());
            assertEquals("Ediciones Godot", firstBook.publisherName());

            assertEquals(
                    0,
                    new BigDecimal("21999")
                            .compareTo(firstBook.retailPrice())
            );
        }
    }

    @Test
    void shouldParseByrPriceListWithExtendedMetadata() throws Exception {
        try (
                InputStream inputStream = getClass().getResourceAsStream("/price-lists/byr.xlsx");
                Workbook workbook = WorkbookFactory.create(inputStream)
        ) {
            PriceListImportConfig config = createByrConfig();
            List<PriceListRow> rows = parser.parse(workbook, config);

            assertFalse(rows.isEmpty());

            PriceListRow first = rows.getFirst();

            assertEquals(4, first.rowNumber());
            assertEquals("9789878826134", first.isbn());
            assertEquals("¡Esta es una chica especial!", first.title());
            assertEquals("Gianina Covezzi", first.authorName());
            assertEquals("Aguinaldo", first.publisherName());
            int actual = new BigDecimal("14500").compareTo(first.retailPrice());
            assertEquals(0, actual);

            assertEquals(0, actual, () -> "Precio leído: " + first.retailPrice());
            assertNotNull(first.metadata());
            assertEquals(134, first.metadata().pageCount());
            assertEquals(0, new BigDecimal("197").compareTo(first.metadata().weightGrams()));
            assertEquals(2022, first.metadata().publicationYear());
            assertEquals(2, first.metadata().publicationMonth());
            assertEquals("Español", first.metadata().language());
            assertEquals("Cuentos", first.metadata().genreName());
            assertEquals("Cuentos, Literatura Argentina", first.metadata().tags());
            assertNotNull(first.metadata().sourceCoverUrl());
            assertFalse(first.metadata().description().isBlank());
        }
    }

    private PriceListImportConfig createByrConfig() {
        PriceListImportConfig config = PriceListImportConfig.builder()
                .name("B&R test")
                .sheetStrategy(SheetStrategy.BY_INDEX)
                .sheetIndex(0)
                .headerStrategy(HeaderStrategy.FIXED_ROW)
                .headerRowIndex(2)
                .firstDataRowIndex(3)
                .active(true)
                .build();

        config.getMappings().addAll(List.of(
                mapping(config, PriceListField.ISBN, 0, "ISBN", PriceListValueType.ISBN, false),
                mapping(config, PriceListField.TITLE, 1, "Título", PriceListValueType.TEXT, true),
                mapping(config, PriceListField.AUTHOR, 2, "Autor", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.RETAIL_PRICE, 3, "PVP", PriceListValueType.DECIMAL, true),
                mapping(config, PriceListField.PUBLISHER, 4, "Editorial", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.PAGE_COUNT, 5, "Páginas", PriceListValueType.INTEGER, false),
                mapping(config, PriceListField.DIMENSIONS, 6, "Tamaño (cms)", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.WEIGHT, 7, "Peso (kgs)", PriceListValueType.DECIMAL, false),
                mapping(config, PriceListField.PUBLICATION_DATE, 8, "Fecha publicación", PriceListValueType.DATE, false),
                mapping(config, PriceListField.LANGUAGE, 9, "Idioma", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.GENRE, 10, "Género", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.TAGS, 11, "Temas/Etiquetas", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.COVER_URL, 12, "Imagen de tapa", PriceListValueType.URL, false),
                mapping(config, PriceListField.DESCRIPTION, 13, "Sinopsis", PriceListValueType.TEXT, false)
        ));

        return config;
    }

    private PriceListImportConfig createCarbonoConfig() {
        PriceListImportConfig config = PriceListImportConfig.builder()
                .name("Carbono test")
                .sheetStrategy(SheetStrategy.BY_INDEX)
                .sheetIndex(0)
                .headerStrategy(HeaderStrategy.FIXED_ROW)
                .headerRowIndex(6)
                .firstDataRowIndex(7)
                .active(true)
                .build();

        config.getMappings().addAll(List.of(
                mapping(config, PriceListField.TITLE, 0, "Título", PriceListValueType.TEXT, true),
                mapping(config, PriceListField.AUTHOR, 1, "Autor", PriceListValueType.TEXT, false),
                mapping(config, PriceListField.ISBN, 2, "Código de barras", PriceListValueType.ISBN, false),
                mapping(config, PriceListField.RETAIL_PRICE, 3, "PVP NOV", PriceListValueType.DECIMAL, true),
                mapping(config, PriceListField.PUBLISHER, 4, "Editorial", PriceListValueType.TEXT, false)
        ));

        return config;
    }

    private PriceListImportConfig createLuongoConfig() {

        PriceListImportConfig config =
                PriceListImportConfig.builder()
                        .name("Luongo test")
                        .sheetStrategy(SheetStrategy.BY_INDEX)
                        .sheetIndex(0)
                        .headerStrategy(HeaderStrategy.FIXED_ROW)
                        .headerRowIndex(0)
                        .firstDataRowIndex(1)
                        .active(true)
                        .build();

        config.getMappings().addAll(
                List.of(
                        mapping(
                                config,
                                PriceListField.ISBN,
                                0,
                                "Isbn",
                                PriceListValueType.ISBN,
                                false
                        ),
                        mapping(
                                config,
                                PriceListField.TITLE,
                                1,
                                "Titulo",
                                PriceListValueType.TEXT,
                                true
                        ),
                        mapping(
                                config,
                                PriceListField.AUTHOR,
                                2,
                                "Autor",
                                PriceListValueType.TEXT,
                                false
                        ),
                        mapping(
                                config,
                                PriceListField.COLLECTION,
                                3,
                                "Coleccion",
                                PriceListValueType.TEXT,
                                false
                        ),
                        mapping(
                                config,
                                PriceListField.PUBLISHER,
                                4,
                                "Sello",
                                PriceListValueType.TEXT,
                                false
                        ),
                        mapping(
                                config,
                                PriceListField.RETAIL_PRICE,
                                6,
                                "Precio",
                                PriceListValueType.DECIMAL,
                                true
                        )
                )
        );

        return config;
    }

    private PriceListColumnMapping mapping(
            PriceListImportConfig config,
            PriceListField targetField,
            int columnIndex,
            String expectedHeader,
            PriceListValueType valueType,
            boolean required
    ) {
        return PriceListColumnMapping.builder()
                .importConfig(config)
                .targetField(targetField)
                .columnIndex(columnIndex)
                .expectedHeader(expectedHeader)
                .valueType(valueType)
                .required(required)
                .active(true)
                .build();
    }
}