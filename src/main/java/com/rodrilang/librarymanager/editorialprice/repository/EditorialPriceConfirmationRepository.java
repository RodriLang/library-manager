package com.rodrilang.librarymanager.editorialprice.repository;

import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceConfirmation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EditorialPriceConfirmationRepository extends JpaRepository<EditorialPriceConfirmation, Long> {

    @EntityGraph(attributePaths = "provider")
    List<EditorialPriceConfirmation> findByEditorialPriceIdInOrderByConfirmedOnDescIdDesc(
            Collection<Long> editorialPriceIds
    );
}