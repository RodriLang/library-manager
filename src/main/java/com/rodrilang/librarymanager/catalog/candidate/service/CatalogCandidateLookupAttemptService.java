package com.rodrilang.librarymanager.catalog.candidate.service;

import com.rodrilang.librarymanager.catalog.candidate.dto.internal.CatalogCandidateLookupData;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.catalog.candidate.repository.CatalogCandidateRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CatalogCandidateLookupAttemptService {

    private final CatalogCandidateRepository repository;

    @Transactional
    public CatalogCandidateLookupData begin(Long candidateId) {
        CatalogCandidate candidate = repository.findDetailedByIdForUpdate(candidateId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró el candidato de catálogo"
                        )
                );

        if (candidate.getStatus() != CatalogCandidateStatus.PENDING) {
            throw new BusinessException(
                    "El candidato no se encuentra pendiente de resolución"
            );
        }

        if (candidate.getAutomaticLookupAttemptedAt() != null) {
            throw new BusinessException(
                    "Ya se realizó una búsqueda automática para este ISBN"
            );
        }

        Instant attemptedAt = Instant.now();

        candidate.setAutomaticLookupAttemptedAt(attemptedAt);
        repository.save(candidate);

        return new CatalogCandidateLookupData(
                candidate.getId(),
                candidate.getIsbn13(),
                attemptedAt
        );
    }

    @Transactional
    public void clear(Long candidateId) {
        repository.findDetailedByIdForUpdate(candidateId)
                .ifPresent(candidate -> {
                    if (candidate.getStatus() == CatalogCandidateStatus.PENDING) {
                        candidate.setAutomaticLookupAttemptedAt(null);
                        repository.save(candidate);
                    }
                });
    }
}