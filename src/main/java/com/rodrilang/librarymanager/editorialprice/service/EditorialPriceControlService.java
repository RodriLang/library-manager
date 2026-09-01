package com.rodrilang.librarymanager.editorialprice.service;

import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConfirmationRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConflictResolutionRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceMetadataUpdateRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.ManualEditorialPriceRequest;

import java.time.LocalDate;

public interface EditorialPriceControlService {

    void createManualPrice(Long bookId, ManualEditorialPriceRequest request, String username);

    void confirmPrice(Long editorialPriceId, EditorialPriceConfirmationRequest request, String username);

    void resolveConflict(Long bookId, LocalDate validFrom, EditorialPriceConflictResolutionRequest request, String username);

    void replaceResolution(Long resolutionId, EditorialPriceConflictResolutionRequest request, String username);

    void deactivateResolution(Long resolutionId, String note, String username);

    void updatePriceMetadata(Long editorialPriceId, EditorialPriceMetadataUpdateRequest request);

    void deactivatePrice(Long editorialPriceId, String note, String username);
}