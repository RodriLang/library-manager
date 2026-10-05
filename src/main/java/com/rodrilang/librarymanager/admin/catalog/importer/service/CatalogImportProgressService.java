package com.rodrilang.librarymanager.admin.catalog.importer.service;

import com.rodrilang.librarymanager.admin.catalog.importer.model.*;
import com.rodrilang.librarymanager.admin.catalog.importer.repository.CatalogImportJobRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CatalogImportProgressService {
    private final CatalogImportJobRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long id) {
        CatalogImportJob j=require(id); j.setStatus(CatalogImportStatus.PROCESSING); j.setPhase(CatalogImportPhase.READING); j.setStartedAt(Instant.now());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setTotalRows(Long id, int totalRows) { CatalogImportJob j=require(id); j.setTotalRows(totalRows); j.setPhase(CatalogImportPhase.CATALOG); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void add(Long id, CatalogImportBatchResult r) {
        CatalogImportJob j=require(id);
        j.setProcessedRows(j.getProcessedRows()+r.processedRows());
        j.setCreatedBooks(j.getCreatedBooks()+r.createdBooks());
        j.setEnrichedBooks(j.getEnrichedBooks()+r.enrichedBooks());
        j.setUnchangedBooks(j.getUnchangedBooks()+r.unchangedBooks());
        j.setConflictedBooks(j.getConflictedBooks()+r.conflictedBooks());
        j.setSkippedRows(j.getSkippedRows()+r.skippedRows());
        j.setErrorCount(j.getErrorCount()+r.errorCount());
        j.setProviderLinksCreated(j.getProviderLinksCreated()+r.providerLinksCreated());
        j.setProviderLinksUpdated(j.getProviderLinksUpdated()+r.providerLinksUpdated());
        j.setPhase(CatalogImportPhase.CATALOG);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id) { CatalogImportJob j=require(id); j.setStatus(CatalogImportStatus.COMPLETED); j.setPhase(CatalogImportPhase.COMPLETED); j.setFinishedAt(Instant.now()); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id,String message) { CatalogImportJob j=require(id); j.setStatus(CatalogImportStatus.FAILED); j.setFailureMessage(message); j.setFinishedAt(Instant.now()); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelled(Long id) { CatalogImportJob j=require(id); j.setStatus(CatalogImportStatus.CANCELLED); j.setFinishedAt(Instant.now()); }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean cancellationRequested(Long id) { return require(id).getStatus()==CatalogImportStatus.CANCEL_REQUESTED; }
    private CatalogImportJob require(Long id){ return repository.findById(id).orElseThrow(()->new BusinessException("No se encontró la importación de catálogo.")); }
}
