package com.rodrilang.librarymanager.importer.price.configuration.model;

import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configuración transitoria de una columna del parser de listas locales. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceListColumnMapping {

    private PriceListField targetField;
    private Integer columnIndex;
    private String expectedHeader;
    private PriceListValueType valueType;

    @Builder.Default
    private boolean required = false;

    @Builder.Default
    private boolean active = true;
}
