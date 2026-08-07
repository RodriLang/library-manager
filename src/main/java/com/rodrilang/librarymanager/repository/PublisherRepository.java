package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Publisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameNormalized(String nameNormalized);

    List<Publisher> findByNameNormalizedIn(Collection<String> names);

    boolean existsByNameNormalized(String nameNormalized);

    Page<Publisher> findByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );
}