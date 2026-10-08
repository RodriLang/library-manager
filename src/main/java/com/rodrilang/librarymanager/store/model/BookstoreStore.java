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
}
