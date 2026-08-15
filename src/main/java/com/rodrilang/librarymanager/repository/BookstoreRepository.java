package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Bookstore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookstoreRepository extends JpaRepository<Bookstore, Long>, JpaSpecificationExecutor<Bookstore> {

    @Query("""
            SELECT b
            FROM Bookstore b
            WHERE (:search IS NULL
                   OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:active IS NULL
                   OR b.active = :active)
            """)
    Page<Bookstore> findAllForAdmin(
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
