package com.rodrilang.librarymanager.provider.bookstore.repository;

import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookstoreProviderRepository extends JpaRepository<BookstoreProvider,Long> {
    @EntityGraph(attributePaths="provider")
    List<BookstoreProvider> findAllByBookstoreIdAndActiveTrueOrderByProviderNameAsc(Long bookstoreId);
    @EntityGraph(attributePaths="provider")
    Optional<BookstoreProvider> findByBookstoreIdAndProviderId(Long bookstoreId,Long providerId);
    boolean existsByBookstoreIdAndProviderIdAndActiveTrue(Long bookstoreId,Long providerId);
}
