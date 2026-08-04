package com.rodrilang.librarymanager.auth.repositories;

import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(RoleType roleName);

    boolean existsByRoleName(RoleType roleName);
}
