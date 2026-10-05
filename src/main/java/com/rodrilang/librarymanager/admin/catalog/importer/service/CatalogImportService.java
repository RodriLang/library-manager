package com.rodrilang.librarymanager.admin.catalog.importer.service;

import com.rodrilang.librarymanager.admin.catalog.importer.dto.*;
import com.rodrilang.librarymanager.admin.catalog.importer.model.*;
import com.rodrilang.librarymanager.admin.catalog.importer.repository.*;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogImportService {
    private final CatalogImportJobRepository jobRepository;
    private final CatalogImportJobErrorRepository errorRepository;
    private final CatalogImportFormatRepository formatRepository;
    private final ProviderRepository providerRepository;
    private final PriceListImportFileStorage fileStorage;
    private final CatalogImportAsyncProcessor asyncProcessor;
    private final BookstoreContext bookstoreContext;

    public CatalogImportStartResponse start(Long providerId,Long formatId,MultipartFile file){
        Provider provider=providerRepository.findById(providerId).orElseThrow(()->new BusinessException("No se encontró el proveedor."));
        if(provider.getType()!=ProviderType.COMMERCIAL || !provider.isActive()) throw new BusinessException("El proveedor no está disponible.");
        CatalogImportFormat format=formatRepository.findById(formatId).orElseThrow(()->new BusinessException("No se encontró el formato."));
        if(!format.isActive() || !format.getProvider().getId().equals(providerId)) throw new BusinessException("El formato no corresponde al proveedor seleccionado.");
        Path path=fileStorage.store(file);
        CatalogImportJob job=CatalogImportJob.builder().provider(provider).format(format).requestedByUserId(bookstoreContext.getCurrentUserId())
                .originalFilename(file.getOriginalFilename()).temporaryFilePath(path.toString()).status(CatalogImportStatus.PENDING)
                .phase(CatalogImportPhase.READING).build();
        job=jobRepository.saveAndFlush(job);
        asyncProcessor.process(job.getId());
        return new CatalogImportStartResponse(job.getId());
    }

    @Transactional(readOnly=true)
    public CatalogImportJobResponse find(Long id){ return toResponse(jobRepository.findDetailedById(id).orElseThrow(()->new BusinessException("No se encontró la importación.")),true); }

    @Transactional(readOnly=true)
    public Page<CatalogImportJobResponse> history(Pageable pageable){ return jobRepository.findAllByOrderByIdDesc(pageable).map(j->toResponse(j,false)); }

    @Transactional
    public CatalogImportJobResponse cancel(Long id){
        CatalogImportJob job=jobRepository.findDetailedById(id).orElseThrow(()->new BusinessException("No se encontró la importación."));
        if(job.getStatus()==CatalogImportStatus.PENDING || job.getStatus()==CatalogImportStatus.PROCESSING) job.setStatus(CatalogImportStatus.CANCEL_REQUESTED);
        return toResponse(job,true);
    }

    private CatalogImportJobResponse toResponse(CatalogImportJob j,boolean includeErrors){
        List<CatalogImportJobResponse.ErrorResponse> errors=includeErrors?errorRepository.findTop200ByJobIdOrderByIdAsc(j.getId()).stream()
                .map(e->new CatalogImportJobResponse.ErrorResponse(e.getRowNumber(),e.getIsbn(),e.getTitle(),e.getErrorType(),e.getMessage())).toList():List.of();
        return new CatalogImportJobResponse(j.getId(),j.getProvider().getId(),j.getProvider().getName(),j.getFormat().getId(),j.getFormat().getName(),j.getOriginalFilename(),
                j.getStatus(),j.getPhase(),j.getTotalRows(),j.getProcessedRows(),j.getCreatedBooks(),j.getEnrichedBooks(),j.getUnchangedBooks(),j.getConflictedBooks(),j.getSkippedRows(),j.getErrorCount(),
                j.getProviderLinksCreated(),j.getProviderLinksUpdated(),j.getStartedAt(),j.getFinishedAt(),j.getFailureMessage(),errors);
    }
}
