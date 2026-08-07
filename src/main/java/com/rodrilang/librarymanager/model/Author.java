package com.rodrilang.librarymanager.model;

import com.rodrilang.librarymanager.util.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "authors")
public class Author extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(
            name = "name_normalized",
            nullable = false
    )
    private String nameNormalized;

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        if (name != null) {
            name = name.trim().replaceAll("\\s+", " ");
        }
        this.nameNormalized = TextNormalizer.normalizeForMatch(name);
    }
}