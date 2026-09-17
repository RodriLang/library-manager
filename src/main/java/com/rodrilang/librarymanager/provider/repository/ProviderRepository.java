package com.rodrilang.librarymanager.provider.repository;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    List<Provider> findAllByActiveTrueOrderByNameAsc();

    List<Provider> findAllByActiveTrueAndTypeOrderByNameAsc(ProviderType type);

    boolean existsByCodeIgnoreCase(String code);
}
