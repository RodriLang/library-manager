package com.rodrilang.librarymanager.auth.repositories;

import com.rodrilang.librarymanager.auth.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {
            "bookstore",
            "roles"
    })
    @Query("""
            SELECT u
            FROM User u
            WHERE u.username = :identifier
               OR u.email = :identifier
            """)
    Optional<User> findByUsernameOrEmail(
            @Param("identifier") String identifier
    );

    @EntityGraph(attributePaths = {
            "bookstore",
            "roles"
    })
    Optional<User> findByIdAndEnabledTrueAndAccountLockedFalse(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByPasswordResetTokenAndEnabledTrueAndAccountLockedFalse(
            String passwordResetToken
    );

    @EntityGraph(attributePaths = {
            "bookstore",
            "roles"
    })
    @Query("""
            SELECT u
            FROM User u
            WHERE u.id = :userId
            """)
    Optional<User> findByIdForAdmin(
            @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = {
            "bookstore"
    })
    @Query("""
            SELECT u
            FROM User u
            WHERE u.bookstore.id = :bookstoreId
              AND (
                  :search IS NULL
                  OR :search = ''
                  OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(CONCAT(u.firstName, ' ', u.lastName))
                        LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (:enabled IS NULL OR u.enabled = :enabled)
              AND (:locked IS NULL OR u.accountLocked = :locked)
            """)
    Page<User> findAllForAdminByBookstore(
            @Param("bookstoreId") Long bookstoreId,
            @Param("search") String search,
            @Param("enabled") Boolean enabled,
            @Param("locked") Boolean locked,
            Pageable pageable
    );

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}