package com.rodrilang.librarymanager.auth.access.repository;

import com.rodrilang.librarymanager.auth.access.model.BookstoreMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookstoreMembershipRepository extends JpaRepository<BookstoreMembership, Long> {
    @EntityGraph(attributePaths = {"bookstore", "roles"})
    List<BookstoreMembership> findAllByUser_IdOrderByBookstore_NameAsc(Long userId);

    @EntityGraph(attributePaths = {"bookstore", "roles"})
    List<BookstoreMembership> findAllByUser_IdAndEnabledTrueOrderByBookstore_NameAsc(Long userId);

    @EntityGraph(attributePaths = {"bookstore", "roles"})
    Optional<BookstoreMembership> findByUser_IdAndBookstore_Id(Long userId, Long bookstoreId);

    boolean existsByUser_IdAndBookstore_IdAndEnabledTrue(Long userId, Long bookstoreId);

    @EntityGraph(attributePaths = {"user", "bookstore", "roles"})
    @Query("select m from BookstoreMembership m where m.bookstore.id = :bookstoreId")
    List<BookstoreMembership> findAllForBookstore(@Param("bookstoreId") Long bookstoreId);
}
