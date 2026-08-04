package com.rodrilang.librarymanager.auth.repositories;

import com.rodrilang.librarymanager.auth.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {
            "bookstore",
            "roles"
    })
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}