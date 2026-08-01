package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.parser.ConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.parser.PriceListParser;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PriceListImportParserResolver {

    private final List<PriceListParser> legacyParsers;
    private final ConfigurablePriceListParser configurableParser;

    public List<PriceListRow> parse(Workbook workbook, PriceListImportJob job) {
        if (job.getImportConfig() != null) {
            return configurableParser.parse(workbook, job.getImportConfig());
        }

        PriceListParser parser = resolveLegacyParser(job);
        parser.validateTemplate(workbook);

        return parser.parse(workbook);
    }

    private PriceListParser resolveLegacyParser(PriceListImportJob job) {
        if (job.getPriceListSource() == null) {
            throw new BusinessException("El trabajo de importación no tiene una fuente configurada.");
        }

        return legacyParsers.stream()
                .filter(parser -> parser.supports(job.getPriceListSource()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "No existe un parser configurado para la lista: " + job.getPriceListSource()
                ));
    }
}