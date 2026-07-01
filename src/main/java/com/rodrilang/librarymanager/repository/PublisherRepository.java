package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameIgnoreCase(String name);

    List<Publisher> findByNameContainingIgnoreCase(String name);

    @Query("""
            SELECT p
            FROM Publisher p
            WHERE lower(p.name) IN :names
            """)
    List<Publisher> findAllNormalizedIn(Collection<String> names);

    @Query("""
            SELECT COUNT(p) > 0
            FROM Publisher p
            WHERE function('unaccent', lower(p.name))
                =
                  function('unaccent', lower(:name))
            """)
    boolean existsNormalized(String name);

}
