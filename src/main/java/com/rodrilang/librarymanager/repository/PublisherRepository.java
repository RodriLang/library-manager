package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameIgnoreCase(String name);

    List<Publisher> findByNameContainingIgnoreCase(String name);

    @Query("""
            SELECT COUNT(a) > 0
            FROM Author a
            WHERE function('unaccent', lower(a.name))
                =
                  function('unaccent', lower(:name))
            """)
    boolean existsNormalized(String name);

}
