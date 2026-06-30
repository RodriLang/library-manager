package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByBookId(Long bookId);

    Optional<Inventory> findByBookIsbn(String isbn);

    boolean existsByBookId(Long bookId);
}
