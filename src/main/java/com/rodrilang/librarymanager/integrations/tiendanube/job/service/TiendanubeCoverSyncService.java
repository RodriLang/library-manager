package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeCoverSyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TiendanubeCoverSyncService {

    private final TiendanubeClient client;

    public TiendanubeCoverSyncResult sync(Long storeId, Long productId, String coverUrl, String lastSyncedCoverUrl) {
        if (coverUrl == null || coverUrl.isBlank() || Objects.equals(coverUrl, lastSyncedCoverUrl)) {
            return TiendanubeCoverSyncResult.unchanged();
        }

        TiendanubeProductResponse product = client.getProduct(storeId, productId);
        TiendanubeImageResponse alreadyPresent = findBySource(product, coverUrl);

        if (alreadyPresent != null) {
            return new TiendanubeCoverSyncResult(alreadyPresent.id(), coverUrl);
        }

        TiendanubeImageResponse currentMainImage = findMainImage(product);
        TiendanubeImageResponse newImage = client.createProductImage(
                storeId,
                productId,
                new TiendanubeCreateImageRequest(coverUrl, 1)
        );

        if (newImage == null) {
            throw new BusinessException("Tiendanube no devolvió la imagen creada");
        }

        if (currentMainImage != null && !Objects.equals(currentMainImage.id(), newImage.id())) {
            client.deleteProductImage(storeId, productId, currentMainImage.id());
        }

        return new TiendanubeCoverSyncResult(newImage.id(), coverUrl);
    }

    private TiendanubeImageResponse findBySource(TiendanubeProductResponse product, String coverUrl) {
        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }

        return product.images().stream()
                .filter(image -> Objects.equals(image.src(), coverUrl))
                .findFirst()
                .orElse(null);
    }

    private TiendanubeImageResponse findMainImage(TiendanubeProductResponse product) {
        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }

        return product.images().stream()
                .filter(image -> Integer.valueOf(1).equals(image.position()))
                .findFirst()
                .orElse(product.images().getFirst());
    }
}
