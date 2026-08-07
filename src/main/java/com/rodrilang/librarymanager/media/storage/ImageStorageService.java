package com.rodrilang.librarymanager.media.storage;

public interface ImageStorageService {

    StoredImage upload(ImageUploadRequest request);

    void delete(String publicId);
}