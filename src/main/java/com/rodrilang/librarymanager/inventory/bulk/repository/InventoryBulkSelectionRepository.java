package com.rodrilang.librarymanager.inventory.bulk.repository;

import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public class InventoryBulkSelectionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Long> findIds(Specification<Inventory> specification, Set<Long> excludedIds) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);

        Root<Inventory> root = query.from(Inventory.class);

        List<Predicate> predicates = new ArrayList<>();

        Predicate specificationPredicate =
                specification.toPredicate(
                        root,
                        query,
                        criteriaBuilder
                );

        if (specificationPredicate != null) {
            predicates.add(specificationPredicate);
        }

        if (excludedIds != null && !excludedIds.isEmpty()) {
            predicates.add(criteriaBuilder.not(root.get("id").in(excludedIds)));
        }

        query.select(root.get("id"))
                .distinct(true)
                .where(criteriaBuilder.and(predicates.toArray(Predicate[]::new)))
                .orderBy(criteriaBuilder.asc(root.get("id")));

        return entityManager.createQuery(query).getResultList();
    }
}