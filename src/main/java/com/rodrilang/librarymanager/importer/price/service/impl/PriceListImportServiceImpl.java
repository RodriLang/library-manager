package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportResult;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.PriceListValidationResult;
import com.rodrilang.librarymanager.importer.price.factory.ImportContextFactory;
import com.rodrilang.librarymanager.importer.price.parser.PriceListParser;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.service.PriceListBookUpsertService;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportErrorValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportSafetyValidator;
import com.rodrilang.librarymanager.importer.price.validator.PriceListValidationService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListImportServiceImpl implements PriceListImportService {

    private final List<PriceListParser> parsers;
    private final BookRepository bookRepository;
    private final PriceListImportSafetyValidator safetyValidator;
    private final PriceListImportErrorValidator importErrorValidator;
    private final PriceListBookUpsertService bookUpsertService;
    private final PriceListValidationService validationService;
    private final ImportContextFactory importContextFactory;

    @Transactional
    @Override
    public synchronized PriceListImportResult importPriceList(PriceListSource priceListSource, MultipartFile file) {
        log.info("Importing price list of {}", priceListSource);

        PriceListParser parser = resolveParser(priceListSource);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            parser.validateTemplate(workbook);

            List<PriceListRow> rows = parser.parse(workbook);

            PriceListValidationResult validation = validationService.validate(rows);

            safetyValidator.validate(rows, validation.validRows());

            validation.errors().forEach(error ->
                    log.warn(
                            "Import error row={} isbn={} severity={} message={}",
                            error.rowNumber(),
                            error.isbn(),
                            error.severity(),
                            error.message()
                    )
            );

            importErrorValidator.validate(validation.errors());

            ImportContext context = importContextFactory.create(validation.validRows());

            int createdBooks = 0;
            int updatedBooks = 0;

            List<Book> booksToSave = new ArrayList<>();
            LocalDate today = LocalDate.now(ZoneId.systemDefault());

            for (PriceListRow row : validation.validRows()) {
                boolean existed = bookUpsertService.exists(row, context);

                Book book = bookUpsertService.upsert(row, context, today);
                booksToSave.add(book);

                if (existed) {
                    updatedBooks++;
                } else {
                    createdBooks++;
                }
            }

            bookRepository.saveAll(booksToSave);

            return new PriceListImportResult(
                    rows.size(),
                    createdBooks,
                    updatedBooks,
                    validation.errors().size(),
                    validation.errors()
            );

        } catch (IOException ex) {
            throw new BusinessException(
                    "No se pudo leer el archivo Excel: " + ex.getMessage()
            );
        }
    }

    private PriceListParser resolveParser(PriceListSource priceListSource) {
        return parsers.stream()
                .filter(parser -> parser.supports(priceListSource))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "No existe un parser configurado para la lista: " + priceListSource
                ));
    }
}