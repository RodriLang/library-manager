package com.rodrilang.librarymanager.inventory.image.model;

import com.rodrilang.librarymanager.media.storage.StoredImage;
import com.rodrilang.librarymanager.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_image_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryImageAsset extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    @Column(name = "cloudinary_public_id", nullable = false, length = 255, unique = true)
    private String cloudinaryPublicId;

    @Column(name = "secure_url", nullable = false, columnDefinition = "TEXT")
    private String secureUrl;

    @Column(length = 20)
    private String format;

    private Integer width;
    private Integer height;

    @Column(name = "file_size")
    private Long fileSize;

    public static InventoryImageAsset from(String contentHash, StoredImage storedImage) {
        return InventoryImageAsset.builder()
                .contentHash(contentHash)
                .cloudinaryPublicId(storedImage.publicId())
                .secureUrl(storedImage.secureUrl())
                .format(storedImage.format())
                .width(storedImage.width())
                .height(storedImage.height())
                .fileSize(storedImage.bytes())
                .build();
    }
}
