package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeCoverSyncResult;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TiendanubeCoverSyncService {

    private final TiendanubeClient client;
    private final TiendanubeCoverSyncStateService stateService;

    public TiendanubeCoverSyncResult sync(Long linkId, Long storeId, Long productId, String coverUrl,
                                          String lastSyncedCoverUrl, Long lastSyncedImageId,
                                          String pendingCoverUrl, String pendingExistingImageIds) {
        TiendanubeProductResponse product = null;

        if (hasText(pendingCoverUrl)) {
            product = client.getProduct(storeId, productId);
            TiendanubeImageResponse recovered = recoverPendingImage(product, pendingExistingImageIds);

            if (Objects.equals(pendingCoverUrl, coverUrl)) {
                if (recovered != null) {
                    deletePreviousManagedImage(storeId, productId, lastSyncedImageId, recovered.id());
                    return TiendanubeCoverSyncResult.synced(recovered.id(), coverUrl);
                }

                return createImage(storeId, productId, coverUrl, lastSyncedImageId);
            }

            if (recovered != null) {
                deletePreviousManagedImage(storeId, productId, recovered.id(), null);
                product = client.getProduct(storeId, productId);
            }
        }

        if (!hasText(coverUrl)) {
            deletePreviousManagedImage(storeId, productId, lastSyncedImageId, null);
            return TiendanubeCoverSyncResult.removed();
        }

        if (Objects.equals(coverUrl, lastSyncedCoverUrl)) {
            return TiendanubeCoverSyncResult.unchanged();
        }

        if (product == null) {
            product = client.getProduct(storeId, productId);
        }

        stateService.begin(linkId, coverUrl, serializeImageIds(product));
        return createImage(storeId, productId, coverUrl, lastSyncedImageId);
    }

    private TiendanubeCoverSyncResult createImage(Long storeId, Long productId, String coverUrl,
                                                   Long lastSyncedImageId) {
        TiendanubeImageResponse newImage = client.createProductImage(
                storeId,
                productId,
                new TiendanubeCreateImageRequest(coverUrl, 1)
        );

        if (newImage == null || newImage.id() == null) {
            throw new BusinessException("Tiendanube no devolvió la imagen creada");
        }

        deletePreviousManagedImage(storeId, productId, lastSyncedImageId, newImage.id());
        return TiendanubeCoverSyncResult.synced(newImage.id(), coverUrl);
    }

    private TiendanubeImageResponse recoverPendingImage(TiendanubeProductResponse product, String baselineValue) {
        Set<Long> baseline = parseImageIds(baselineValue);

        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }

        var candidates = product.images().stream()
                .filter(image -> image.id() != null && !baseline.contains(image.id()))
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        var mainCandidates = candidates.stream()
                .filter(image -> Integer.valueOf(1).equals(image.position()))
                .toList();

        if (mainCandidates.size() == 1) {
            return mainCandidates.getFirst();
        }

        throw TiendanubeJobExecutionException.nonRetryable(
                "COVER_SYNC_AMBIGUOUS",
                "No se pudo determinar de forma segura qué imagen fue creada por el intento anterior",
                null
        );
    }

    private void deletePreviousManagedImage(Long storeId, Long productId, Long previousImageId, Long newImageId) {
        if (previousImageId == null || Objects.equals(previousImageId, newImageId)) {
            return;
        }

        try {
            client.deleteProductImage(storeId, productId, previousImageId);
        } catch (TiendanubeRemoteResourceNotFoundException ignored) {
            // Estado deseado ya alcanzado: la imagen anterior no existe.
        }
    }

    private String serializeImageIds(TiendanubeProductResponse product) {
        if (product.images() == null || product.images().isEmpty()) {
            return "";
        }

        return product.images().stream()
                .map(TiendanubeImageResponse::id)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Set<Long> parseImageIds(String value) {
        if (!hasText(value)) {
            return Set.of();
        }

        Set<Long> ids = new HashSet<>();

        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .forEach(part -> {
                    try {
                        ids.add(Long.parseLong(part));
                    } catch (NumberFormatException exception) {
                        throw TiendanubeJobExecutionException.nonRetryable(
                                "INVALID_COVER_SYNC_STATE",
                                "El estado persistido de sincronización de portada es inválido",
                                exception
                        );
                    }
                });

        return ids;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
