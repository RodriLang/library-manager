package com.rodrilang.librarymanager.purchasing.requirement.repository;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSource;
import com.rodrilang.librarymanager.purchasing.requirement.repository.projection.PurchaseRequirementReasonProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseRequirementSourceRepository extends JpaRepository<PurchaseRequirementSource, Long> {

    List<PurchaseRequirementSource> findAllByRequirementIdOrderByCreatedAtAsc(Long requirementId);

    @Query("""
            SELECT
                prs.requirement.id AS requirementId,
                prs.type AS type,
                SUM(prs.quantity) AS quantity
            FROM PurchaseRequirementSource prs
            WHERE prs.requirement.id IN :requirementIds
            GROUP BY
                prs.requirement.id,
                prs.type
            """)
    List<PurchaseRequirementReasonProjection> findGroupedReasons(
            @Param("requirementIds")
            Collection<Long> requirementIds
    );
}