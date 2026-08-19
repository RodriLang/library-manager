package com.rodrilang.librarymanager.purchasing.requirement.repository;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSource;
import com.rodrilang.librarymanager.purchasing.requirement.repository.projection.PurchaseRequirementReasonProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    @Query("""
            SELECT
                source.requirement.id AS requirementId,
                source.type AS type,
                SUM(source.quantity) AS quantity
            FROM PurchaseRequirementSource source
            WHERE source.requirement.id IN :requirementIds
              AND source.type NOT IN (
                  com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType.ADJUSTMENT,
                  com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType.REVERSAL
              )
              AND NOT EXISTS (
                  SELECT reversal.id
                  FROM PurchaseRequirementSource reversal
                  WHERE reversal.reversedSource.id = source.id
              )
            GROUP BY
                source.requirement.id,
                source.type
            """)
    List<PurchaseRequirementReasonProjection> findEffectiveGroupedReasons(
            @Param("requirementIds")
            Collection<Long> requirementIds
    );

    @EntityGraph(attributePaths = {
            "requirement",
            "provider",
            "reversedSource"
    })
    Optional<PurchaseRequirementSource>
    findByIdAndRequirementId(
            Long id,
            Long requirementId
    );

    boolean existsByReversedSourceId(
            Long reversedSourceId
    );
}