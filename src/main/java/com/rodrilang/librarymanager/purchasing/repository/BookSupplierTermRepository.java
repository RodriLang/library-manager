package com.rodrilang.librarymanager.purchasing.repository;

import com.rodrilang.librarymanager.purchasing.model.BookSupplierTerm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookSupplierTermRepository extends JpaRepository<BookSupplierTerm, Long> {
    Optional<BookSupplierTerm> findBySupplierIdAndBookId(Long supplierId, Long bookId);

    @EntityGraph(attributePaths = {"book", "supplier"})
    List<BookSupplierTerm> findAllBySupplierIdOrderByBookTitleAsc(Long supplierId);
}
