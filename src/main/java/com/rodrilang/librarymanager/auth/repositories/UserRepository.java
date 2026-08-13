package com.rodrilang.librarymanager.auth.repositories;

import com.rodrilang.librarymanager.auth.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

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

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}