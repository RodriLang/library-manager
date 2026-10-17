package com.rodrilang.librarymanager.shipping.quote.model;

import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.store.model.BookstoreStore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_shipping_quotes")
public class StoreShippingQuote {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bookstore_id", nullable = false) private Bookstore bookstore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ShippingProviderType provider;
    @Column(name = "service_code", nullable = false, length = 100) private String serviceCode;
    @Column(name = "service_name", nullable = false, length = 150) private String serviceName;
    @Enumerated(EnumType.STRING) @Column(name = "delivery_type", nullable = false, length = 30) private ShippingDeliveryType deliveryType;
    @Column(name = "destination_postal_code", nullable = false, length = 20) private String destinationPostalCode;
    @Column(name = "items_hash", nullable = false, length = 64) private String itemsHash;
    @Column(name = "provider_amount", nullable = false, precision = 14, scale = 2) private BigDecimal providerAmount;
    @Column(name = "packaging_surcharge", nullable = false, precision = 14, scale = 2) private BigDecimal packagingSurcharge;
    @Column(name = "charged_amount", nullable = false, precision = 14, scale = 2) private BigDecimal chargedAmount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "estimated_days_min") private Integer estimatedDaysMin;
    @Column(name = "estimated_days_max") private Integer estimatedDaysMax;
    @Column(name = "estimated_text", length = 120) private String estimatedText;
    @Column(name = "external_quote_id", length = 160) private String externalQuoteId;
    @Column(name = "free_shipping_applied", nullable = false) private Boolean freeShippingApplied;
    @Column(name = "recommended", nullable = false) private Boolean recommended;
    @Column(name = "sort_priority", nullable = false) private Integer sortPriority;
    @Column(name = "weight_grams", nullable = false) private Integer weightGrams;
    @Column(name = "width_cm", nullable = false, precision = 10, scale = 2) private BigDecimal widthCm;
    @Column(name = "height_cm", nullable = false, precision = 10, scale = 2) private BigDecimal heightCm;
    @Column(name = "depth_cm", nullable = false, precision = 10, scale = 2) private BigDecimal depthCm;
    @Column(name = "volume_cm3", nullable = false, precision = 14, scale = 2) private BigDecimal volumeCm3;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
