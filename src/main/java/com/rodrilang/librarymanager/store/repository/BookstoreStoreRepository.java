package com.rodrilang.librarymanager.store.repository;
import com.rodrilang.librarymanager.store.model.BookstoreStore;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface BookstoreStoreRepository extends JpaRepository<BookstoreStore, Long> {
    Optional<BookstoreStore> findByBookstoreId(Long bookstoreId);
    Optional<BookstoreStore> findByPublicId(UUID publicId);
    Optional<BookstoreStore> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
}
