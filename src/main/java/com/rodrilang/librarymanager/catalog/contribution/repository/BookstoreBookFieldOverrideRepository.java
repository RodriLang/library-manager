package com.rodrilang.librarymanager.catalog.contribution.repository;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.model.BookstoreBookFieldOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookstoreBookFieldOverrideRepository extends JpaRepository<BookstoreBookFieldOverride, Long> {

    Optional<BookstoreBookFieldOverride> findByBookstoreIdAndBook_IdAndField(
            Long bookstoreId,
            Long bookId,
            BookField field
    );

    List<BookstoreBookFieldOverride> findAllByBookstoreIdAndBook_Id(
            Long bookstoreId,
            Long bookId
    );

    void deleteByBookstoreIdAndBook_IdAndField(
            Long bookstoreId,
            Long bookId,
            BookField field
    );

    @Modifying
    @Query("""
            delete from BookstoreBookFieldOverride o
             where o.book.id = :bookId
               and o.field = :field
               and o.value = :value
            """)
    int deleteRedundantOverrides(
            @Param("bookId") Long bookId,
            @Param("field") BookField field,
            @Param("value") String value
    );
}
