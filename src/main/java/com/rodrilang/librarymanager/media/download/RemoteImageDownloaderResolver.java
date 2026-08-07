package com.rodrilang.librarymanager.media.download;

import com.rodrilang.librarymanager.media.download.google.GoogleDriveImageDownloader;
import com.rodrilang.librarymanager.media.download.http.HttpImageDownloader;
import com.rodrilang.librarymanager.media.exception.UnsupportedImageSourceException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RemoteImageDownloaderResolver {

    private final List<RemoteImageDownloader> downloaders;

    public RemoteImageDownloaderResolver(
            GoogleDriveImageDownloader driveDownloader,
            HttpImageDownloader httpDownloader
    ) {
        this.downloaders = List.of(
                driveDownloader,
                httpDownloader
        );
    }

    public RemoteImageDownloader resolve(String sourceUrl) {
        return downloaders.stream()
                .filter(downloader ->
                        downloader.supports(sourceUrl)
                )
                .findFirst()
                .orElseThrow(() ->
                        new UnsupportedImageSourceException(
                                sourceUrl
                        )
                );
    }
}