package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Publisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameNormalized(String nameNormalized);

    List<Publisher> findByNameNormalizedIn(Collection<String> names);

    boolean existsByNameNormalized(String nameNormalized);

    @Query(
            value = """
                    SELECT p.*
                    FROM publishers p
                    WHERE immutable_unaccent(lower(p.name))
                        LIKE CONCAT(
                            '%',
                            immutable_unaccent(lower(:name)),
                            '%'
                        )
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM publishers p
                    WHERE immutable_unaccent(lower(p.name))
                        LIKE CONCAT(
                            '%',
                            immutable_unaccent(lower(:name)),
                            '%'
                        )
                    """,
            nativeQuery = true
    )
    Page<Publisher> searchByName(
            String name,
            Pageable pageable
    );
}