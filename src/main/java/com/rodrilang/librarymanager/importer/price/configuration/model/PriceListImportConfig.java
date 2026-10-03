package com.rodrilang.librarymanager.importer.price.configuration.model;

import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración transitoria usada por el parser de planillas.
 *
 * Ya no es una entidad global persistida: los formatos que guarda cada librería
 * viven en bookstore_price_list_formats y se traducen a este objeto al procesar
 * una importación.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceListImportConfig {
    private String name;
    private SheetStrategy sheetStrategy;
    private Integer sheetIndex;
    private String sheetName;
    private HeaderStrategy headerStrategy;
    private Integer headerRowIndex;
    private Integer firstDataRowIndex;
    @Builder.Default
    private boolean active = true;
    @Builder.Default
    private List<PriceListColumnMapping> mappings = new ArrayList<>();
}
