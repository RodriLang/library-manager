package com.rodrilang.librarymanager.importer.price.model;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.enums.ProviderPublisherMappingType;
import com.rodrilang.librarymanager.model.Publisher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "provider_publisher_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_publisher_mapping_provider_external_name",
                        columnNames = {"provider_id", "external_name_normalized"}
                )
        },
        indexes = {
                @Index(name = "idx_provider_publisher_mapping_publisher", columnList = "publisher_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPublisherMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_provider_publisher_mapping_provider")
    )
    private PriceListProvider provider;

    @Column(name = "external_name", nullable = false, length = 200)
    private String externalName;

    @Column(name = "external_name_normalized", nullable = false, length = 200)
    private String externalNameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", nullable = false, length = 20)
    private ProviderPublisherMappingType resolutionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id",
            foreignKey = @ForeignKey(name = "fk_provider_publisher_mapping_publisher")
    )
    private Publisher publisher;
}