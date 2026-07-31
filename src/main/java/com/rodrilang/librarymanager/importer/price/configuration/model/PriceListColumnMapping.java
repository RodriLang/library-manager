package com.rodrilang.librarymanager.importer.price.configuration.model;

import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "price_list_column_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_price_list_column_mapping_field",
                        columnNames = {
                                "import_config_id",
                                "target_field"
                        }
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceListColumnMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "import_config_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_price_list_column_mappings_config"
            )
    )
    private PriceListImportConfig importConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_field", nullable = false, length = 50)
    private PriceListField targetField;

    @Column(name = "column_index", nullable = false)
    private Integer columnIndex;

    @Column(name = "expected_header", length = 150)
    private String expectedHeader;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 30)
    private PriceListValueType valueType;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}