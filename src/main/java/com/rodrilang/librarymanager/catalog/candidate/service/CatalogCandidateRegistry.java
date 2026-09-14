package com.rodrilang.librarymanager.catalog.candidate.service;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.repository.CatalogCandidateRepository;
import com.rodrilang.librarymanager.catalog.candidate.repository.CatalogCandidateUpsertRepository;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.model.Bookstore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogCandidateRegistry {

    private final CatalogCandidateRepository repository;
    private final CatalogCandidateUpsertRepository upsertRepository;

    public CatalogCandidate getOrCreate(ParsedIsbn isbn, Bookstore bookstore) {
        Long candidateId = upsertRepository.getOrCreate(isbn, bookstore.getId());
        return repository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No se pudo recuperar el candidato de catálogo"));
    }
}
