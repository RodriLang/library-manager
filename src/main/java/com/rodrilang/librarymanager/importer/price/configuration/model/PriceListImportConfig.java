package com.rodrilang.librarymanager.importer.price.configuration.model;

import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_list_import_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceListImportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "provider_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_price_list_import_configs_provider"
            )
    )
    private PriceListProvider provider;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sheet_strategy", nullable = false, length = 30)
    private SheetStrategy sheetStrategy;

    @Column(name = "sheet_index")
    private Integer sheetIndex;

    @Column(name = "sheet_name", length = 200)
    private String sheetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "header_strategy", nullable = false, length = 30)
    private HeaderStrategy headerStrategy;

    @Column(name = "header_row_index")
    private Integer headerRowIndex;

    @Column(name = "first_data_row_index", nullable = false)
    private Integer firstDataRowIndex;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(
            mappedBy = "importConfig",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("columnIndex ASC")
    @Builder.Default
    private List<PriceListColumnMapping> mappings = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}