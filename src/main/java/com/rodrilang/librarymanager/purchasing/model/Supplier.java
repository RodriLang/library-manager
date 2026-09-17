package com.rodrilang.librarymanager.purchasing.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "suppliers", uniqueConstraints = @UniqueConstraint(
        name = "uk_suppliers_bookstore_name", columnNames = {"bookstore_id", "name"}
))
public class Supplier extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "tax_id", length = 30)
    private String taxId;
    @Column(length = 160)
    private String email;
    @Column(length = 50)
    private String phone;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Builder.Default @Column(nullable = false)
    private Boolean active = true;
}
