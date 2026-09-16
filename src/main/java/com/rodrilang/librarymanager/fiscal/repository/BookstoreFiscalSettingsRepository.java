package com.rodrilang.librarymanager.fiscal.repository;

import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookstoreFiscalSettingsRepository extends JpaRepository<BookstoreFiscalSettings, Long> {

    Optional<BookstoreFiscalSettings> findByBookstoreId(Long bookstoreId);

    boolean existsByCuitAndPointOfSaleAndBookstoreIdNot(
            String cuit,
            Integer pointOfSale,
            Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT settings
            FROM BookstoreFiscalSettings settings
            WHERE settings.bookstore.id = :bookstoreId
            """)
    Optional<BookstoreFiscalSettings> findByBookstoreIdForUpdate(
            @Param("bookstoreId") Long bookstoreId
    );
}
