package com.rodrilang.librarymanager.inventory.image.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.image.dto.*;
import com.rodrilang.librarymanager.inventory.image.model.InventoryImage;
import com.rodrilang.librarymanager.inventory.image.model.InventoryImageAsset;
import com.rodrilang.librarymanager.inventory.image.model.InventoryImageSource;
import com.rodrilang.librarymanager.inventory.image.repository.InventoryImageAssetRepository;
import com.rodrilang.librarymanager.inventory.image.repository.InventoryImageRepository;
import com.rodrilang.librarymanager.media.hashing.ImageHashService;
import com.rodrilang.librarymanager.media.image.ImageOptimizer;
import com.rodrilang.librarymanager.media.image.OptimizedImage;
import com.rodrilang.librarymanager.media.storage.*;
import com.rodrilang.librarymanager.media.validation.ImageValidator;
import com.rodrilang.librarymanager.media.validation.ValidatedImage;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryImageService {
    public static final int MAX_IMAGES = 5;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final InventoryRepository inventoryRepository;
    private final InventoryImageRepository imageRepository;
    private final InventoryImageAssetRepository assetRepository;
    private final BookstoreContext bookstoreContext;
    private final ImageOptimizer imageOptimizer;
    private final ImageValidator imageValidator;
    private final ImageHashService imageHashService;
    private final ImageStorageService imageStorageService;
    private final ImageFolderResolver imageFolderResolver;

    @Transactional(readOnly = true)
    public InventoryImageGalleryResponse getGallery(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);
        return buildGallery(inventory, imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventoryId));
    }

    @Transactional(readOnly = true)
    public InventoryImageGalleryResponse getGalleryForInventory(Inventory inventory) {
        Objects.requireNonNull(inventory, "El inventario es obligatorio");
        return buildGallery(inventory, imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventory.getId()));
    }

    @Transactional
    public InventoryImageGalleryResponse upload(Long inventoryId, MultipartFile file) {
        Inventory inventory = getInventory(inventoryId);

        byte[] raw = read(file);
        OptimizedImage optimized = imageOptimizer.optimize(raw, file.getOriginalFilename(), file.getContentType());
        ValidatedImage validated = imageValidator.validate(
                optimized.content(),
                optimized.filename(),
                optimized.optimized() ? "image/jpeg" : file.getContentType()
        );
        String hash = imageHashService.sha256(validated.content());

        Optional<InventoryImage> alreadyAttached = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventoryId)
                .stream()
                .filter(image -> image.getAsset() != null && hash.equals(image.getAsset().getContentHash()))
                .findFirst();
        if (alreadyAttached.isPresent()) {
            return getGallery(inventoryId);
        }

        ensureCapacity(inventory);

        InventoryImageAsset asset = assetRepository.findByContentHash(hash)
                .orElseGet(() -> createAsset(hash, validated));

        imageRepository.save(InventoryImage.builder()
                .inventory(inventory)
                .asset(asset)
                .source(InventoryImageSource.UPLOAD)
                .sortOrder(nextSecondaryOrder(inventory))
                .build());

        return getGallery(inventoryId);
    }

    @Transactional
    public InventoryImageGalleryResponse addUrl(Long inventoryId, String value) {
        Inventory inventory = getInventory(inventoryId);
        String url = normalizeUrl(value);

        if (imageRepository.existsByInventoryIdAndExternalUrl(inventoryId, url)) {
            return getGallery(inventoryId);
        }
        ensureCapacity(inventory);

        if (!imageRepository.existsByInventoryIdAndExternalUrl(inventoryId, url)) {
            imageRepository.save(InventoryImage.builder()
                    .inventory(inventory)
                    .externalUrl(url)
                    .source(InventoryImageSource.URL)
                    .sortOrder(nextSecondaryOrder(inventory))
                    .build());
        }

        return getGallery(inventoryId);
    }

    @Transactional
    public InventoryImageGalleryResponse makePrimary(Long inventoryId, Long imageId) {
        Inventory inventory = getInventory(inventoryId);
        InventoryImage selected = imageRepository.findByIdAndInventoryId(imageId, inventoryId)
                .orElseThrow(() -> new BusinessException("No se encontró la imagen del inventario."));

        List<InventoryImage> custom = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventoryId);
        List<InventoryImage> reordered = new ArrayList<>();
        reordered.add(selected);
        custom.stream().filter(image -> !image.getId().equals(selected.getId())).forEach(reordered::add);
        persistOrders(reordered, hasBookCover(inventory), true);
        return getGallery(inventoryId);
    }

    @Transactional
    public InventoryImageGalleryResponse useBookCoverAsPrimary(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);
        if (!hasBookCover(inventory)) {
            throw new BusinessException("El libro no tiene una portada disponible para usar como principal.");
        }
        List<InventoryImage> custom = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventoryId);
        persistOrders(custom, true, false);
        return getGallery(inventoryId);
    }

    @Transactional
    public InventoryImageGalleryResponse reorder(Long inventoryId, List<Long> imageIds) {
        Inventory inventory = getInventory(inventoryId);
        List<InventoryImage> custom = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventoryId);
        if (custom.size() != imageIds.size()) {
            throw new BusinessException("El orden recibido no contiene todas las imágenes propias.");
        }
        Map<Long, InventoryImage> byId = new HashMap<>();
        custom.forEach(image -> byId.put(image.getId(), image));
        List<InventoryImage> reordered = new ArrayList<>();
        for (Long id : imageIds) {
            InventoryImage image = byId.remove(id);
            if (image == null) {
                throw new BusinessException("El orden recibido contiene una imagen inválida.");
            }
            reordered.add(image);
        }
        boolean customPrimary = custom.stream().anyMatch(image -> image.getSortOrder() == 0);
        persistOrders(reordered, hasBookCover(inventory), customPrimary);
        return getGallery(inventoryId);
    }

    @Transactional
    public InventoryImageGalleryResponse delete(Long inventoryId, Long imageId) {
        Inventory inventory = getInventory(inventoryId);
        InventoryImage image = imageRepository.findByIdAndInventoryId(imageId, inventoryId)
                .orElseThrow(() -> new BusinessException("No se encontró la imagen del inventario."));
        Long assetId = image.getAsset() != null ? image.getAsset().getId() : null;
        boolean wasPrimary = image.getSortOrder() == 0;
        imageRepository.delete(image);
        imageRepository.flush();

        if (assetId != null && imageRepository.countByAssetId(assetId) == 0) {
            assetRepository.findById(assetId).ifPresent(asset -> {
                try {
                    imageStorageService.delete(asset.getCloudinaryPublicId());
                    assetRepository.delete(asset);
                } catch (RuntimeException ex) {
                    log.warn("No se pudo limpiar el asset de imagen {}", assetId, ex);
                }
            });
        }

        List<InventoryImage> remaining = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventoryId);
        boolean remainingCustomPrimary = remaining.stream().anyMatch(item -> item.getSortOrder() == 0);
        persistOrders(remaining, hasBookCover(inventory), remainingCustomPrimary || (wasPrimary && !hasBookCover(inventory)));
        return getGallery(inventoryId);
    }

    private InventoryImageAsset createAsset(String hash, ValidatedImage validated) {
        StoredImage stored = imageStorageService.upload(ImageUploadRequest.create(
                validated.content(),
                validated.originalFilename(),
                imageFolderResolver.inventoryImages(),
                hash
        ));
        try {
            imageValidator.validateStoredDimensions(stored.width(), stored.height());
            return assetRepository.save(InventoryImageAsset.from(hash, stored));
        } catch (DataIntegrityViolationException race) {
            deleteQuietly(stored.publicId());
            return assetRepository.findByContentHash(hash).orElseThrow(() -> race);
        } catch (RuntimeException ex) {
            deleteQuietly(stored.publicId());
            throw ex;
        }
    }

    private void ensureCapacity(Inventory inventory) {
        int custom = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventory.getId()).size();
        int total = custom + (hasBookCover(inventory) ? 1 : 0);
        if (total >= MAX_IMAGES) {
            throw new BusinessException("El inventario puede tener como máximo %d imágenes.".formatted(MAX_IMAGES));
        }
    }

    private int nextSecondaryOrder(Inventory inventory) {
        List<InventoryImage> custom = imageRepository.findAllByInventoryIdOrderBySortOrderAsc(inventory.getId());
        boolean customPrimary = custom.stream().anyMatch(image -> image.getSortOrder() == 0);
        int start = customPrimary ? (hasBookCover(inventory) ? 2 : 1) : (hasBookCover(inventory) ? 1 : 0);
        Set<Integer> used = new HashSet<>();
        custom.forEach(image -> used.add(image.getSortOrder()));
        for (int order = start; order < MAX_IMAGES; order++) {
            if (!used.contains(order)) return order;
        }
        throw new BusinessException("No hay posiciones disponibles para otra imagen.");
    }

    private void persistOrders(List<InventoryImage> images, boolean hasBookCover, boolean customPrimary) {
        int order = customPrimary ? 0 : (hasBookCover ? 1 : 0);
        for (int i = 0; i < images.size(); i++) {
            InventoryImage image = images.get(i);
            if (customPrimary && i == 1 && hasBookCover) order = 2;
            image.setSortOrder(order++);
            imageRepository.save(image);
        }
    }

    private InventoryImageGalleryResponse buildGallery(Inventory inventory, List<InventoryImage> custom) {
        List<InventoryImageResponse> result = new ArrayList<>();
        boolean customPrimary = custom.stream().anyMatch(image -> image.getSortOrder() == 0);
        if (hasBookCover(inventory)) {
            int order = customPrimary ? 1 : 0;
            result.add(new InventoryImageResponse(
                    null,
                    inventory.getBook().getCoverUrl(),
                    "BOOK_COVER",
                    order,
                    !customPrimary,
                    true,
                    null,
                    null,
                    null
            ));
        }
        for (InventoryImage image : custom) {
            InventoryImageAsset asset = image.getAsset();
            result.add(new InventoryImageResponse(
                    image.getId(),
                    image.resolvedUrl(),
                    image.getSource().name(),
                    image.getSortOrder(),
                    image.getSortOrder() == 0,
                    false,
                    asset != null ? asset.getWidth() : null,
                    asset != null ? asset.getHeight() : null,
                    asset != null ? asset.getFileSize() : null
            ));
        }
        result.sort(Comparator.comparing(InventoryImageResponse::order));
        return new InventoryImageGalleryResponse(MAX_IMAGES, List.copyOf(result));
    }

    private Inventory getInventory(Long inventoryId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return inventoryRepository.findByIdAndBookstoreId(inventoryId, bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró el inventario solicitado."));
    }

    private boolean hasBookCover(Inventory inventory) {
        return inventory.getBook() != null
                && inventory.getBook().getCoverUrl() != null
                && !inventory.getBook().getCoverUrl().isBlank();
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("La URL de la imagen es obligatoria.");
        String url = value.trim();
        try {
            URI uri = new URI(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new BusinessException("Ingresá una URL http o https válida.");
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            throw new BusinessException("Ingresá una URL de imagen válida.");
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("Seleccioná una imagen para subir.");
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("La imagen no puede superar los 10 MB antes de la optimización.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer la imagen seleccionada.");
        }
    }

    private void deleteQuietly(String publicId) {
        try {
            imageStorageService.delete(publicId);
        } catch (RuntimeException ex) {
            log.warn("No se pudo eliminar la imagen huérfana {}", publicId, ex);
        }
    }
}
