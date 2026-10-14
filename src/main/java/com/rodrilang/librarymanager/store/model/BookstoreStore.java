package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "bookstore_stores")
public class BookstoreStore extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bookstore_id", nullable = false, unique = true) private Bookstore bookstore;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Column(name = "display_name", nullable = false, length = 150) private String displayName;
    @Enumerated(EnumType.STRING) @Column(name = "title_format", nullable = false, length = 40) @Builder.Default private StoreTitleFormat titleFormat = StoreTitleFormat.TITLE;
    @Column(name = "show_isbn", nullable = false) @Builder.Default private Boolean showIsbn = true;
    @Column(name = "show_author", nullable = false) @Builder.Default private Boolean showAuthor = true;
    @Column(name = "show_publisher", nullable = false) @Builder.Default private Boolean showPublisher = true;
    @Column(name = "show_stock", nullable = false) @Builder.Default private Boolean showStock = false;
    @Column(name = "logo_url", length = 1000) private String logoUrl;
    @Column(name = "favicon_url", length = 1000) private String faviconUrl;
    @Column(name = "primary_color", length = 20) private String primaryColor;
    @Column(name = "secondary_color", length = 20) private String secondaryColor;
    @Column(columnDefinition = "TEXT") private String description;

    @Column(name = "header_show_logo", nullable = false) @Builder.Default private Boolean headerShowLogo = true;
    @Column(name = "header_show_name", nullable = false) @Builder.Default private Boolean headerShowName = true;
    @Enumerated(EnumType.STRING) @Column(name = "header_brand_layout", nullable = false, length = 20) @Builder.Default private StoreHeaderBrandLayout headerBrandLayout = StoreHeaderBrandLayout.INLINE;
    @Enumerated(EnumType.STRING) @Column(name = "header_logo_size", nullable = false, length = 20) @Builder.Default private StoreHeaderLogoSize headerLogoSize = StoreHeaderLogoSize.COMPACT;

    @Column(name = "hero_show_eyebrow", nullable = false) @Builder.Default private Boolean heroShowEyebrow = true;
    @Column(name = "hero_eyebrow", nullable = false, length = 120) @Builder.Default private String heroEyebrow = "LIBRERÍA ONLINE";
    @Column(name = "hero_show_logo", nullable = false) @Builder.Default private Boolean heroShowLogo = false;
    @Column(name = "hero_show_name", nullable = false) @Builder.Default private Boolean heroShowName = true;
    @Column(name = "hero_title", length = 220) private String heroTitle;
    @Column(name = "hero_description", columnDefinition = "TEXT") private String heroDescription;
    @Enumerated(EnumType.STRING) @Column(name = "hero_text_alignment", nullable = false, length = 20) @Builder.Default private StoreHeroTextAlignment heroTextAlignment = StoreHeroTextAlignment.LEFT;
    @Column(name = "hero_show_books", nullable = false) @Builder.Default private Boolean heroShowBooks = true;
    @Enumerated(EnumType.STRING) @Column(name = "hero_books_mode", nullable = false, length = 30) @Builder.Default private StoreHeroBooksMode heroBooksMode = StoreHeroBooksMode.FEATURED_FIXED;
    @Column(name = "hero_primary_button_enabled", nullable = false) @Builder.Default private Boolean heroPrimaryButtonEnabled = true;
    @Column(name = "hero_primary_button_text", nullable = false, length = 80) @Builder.Default private String heroPrimaryButtonText = "Explorar catálogo";
    @Column(name = "hero_secondary_button_enabled", nullable = false) @Builder.Default private Boolean heroSecondaryButtonEnabled = true;
    @Column(name = "hero_secondary_button_text", nullable = false, length = 80) @Builder.Default private String heroSecondaryButtonText = "Ver novedades";

    @Column(name = "discover_enabled", nullable = false) @Builder.Default private Boolean discoverEnabled = true;
    @Column(name = "discover_eyebrow", nullable = false, length = 120) @Builder.Default private String discoverEyebrow = "ENCONTRÁ TU PRÓXIMA LECTURA";
    @Column(name = "discover_title", nullable = false, length = 220) @Builder.Default private String discoverTitle = "Todo el catálogo en un solo lugar";
    @Column(name = "discover_description", nullable = false, columnDefinition = "TEXT") @Builder.Default private String discoverDescription = "Buscá por título, autor, editorial o ISBN y filtrá por categorías, géneros, precio y disponibilidad.";
    @Column(name = "discover_button_text", nullable = false, length = 80) @Builder.Default private String discoverButtonText = "Ver catálogo completo";
}
