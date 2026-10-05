package com.rodrilang.librarymanager.admin.catalog.importer.model;

import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.provider.model.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "catalog_import_formats")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CatalogImportFormat extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sheet_strategy", nullable = false, length = 30)
    private SheetStrategy sheetStrategy;
    @Column(name = "sheet_index") private Integer sheetIndex;
    @Column(name = "sheet_name", length = 150) private String sheetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "header_strategy", nullable = false, length = 30)
    private HeaderStrategy headerStrategy;
    @Column(name = "header_row_index") private Integer headerRowIndex;
    @Column(name = "first_data_row_index", nullable = false) private Integer firstDataRowIndex;

    @Column(nullable = false) @Builder.Default
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "catalog_import_format_mappings", joinColumns = @JoinColumn(name = "format_id"))
    @OrderColumn(name = "mapping_order")
    @Builder.Default
    private List<Mapping> mappings = new ArrayList<>();

    @Embeddable
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Mapping {
        @Enumerated(EnumType.STRING)
        @Column(name = "target_field", nullable = false, length = 40)
        private PriceListField targetField;
        @Column(name = "column_index", nullable = false) private Integer columnIndex;
        @Column(name = "expected_header", length = 200) private String expectedHeader;
        @Enumerated(EnumType.STRING)
        @Column(name = "value_type", nullable = false, length = 20)
        private PriceListValueType valueType;
        @Column(nullable = false) private boolean required;
        @Column(nullable = false) private boolean active;
    }
}
