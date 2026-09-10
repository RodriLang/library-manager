package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeCoverSyncStateService {

    private final TiendanubeProductLinkRepository productLinkRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void begin(Long linkId, String coverUrl, String existingImageIds) {
        TiendanubeProductLink link = requireLink(linkId);
        link.setPendingCoverUrl(coverUrl);
        link.setPendingCoverExistingImageIds(existingImageIds);
        link.setPendingCoverStartedAt(Instant.now());
    }

    private TiendanubeProductLink requireLink(Long linkId) {
        return productLinkRepository.findByIdForUpdate(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el vínculo de Tiendanube"));
    }
}
