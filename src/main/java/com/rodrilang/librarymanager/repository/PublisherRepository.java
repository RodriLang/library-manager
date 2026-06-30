package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByNameIgnoreCase(String name);

    List<Publisher> findByNameContainingIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

}
