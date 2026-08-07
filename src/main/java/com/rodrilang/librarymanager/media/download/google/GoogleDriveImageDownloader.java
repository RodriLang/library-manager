package com.rodrilang.librarymanager.media.download.google;

import com.rodrilang.librarymanager.media.download.DownloadedImage;
import com.rodrilang.librarymanager.media.download.RemoteImageDownloader;
import com.rodrilang.librarymanager.media.download.http.RemoteImageHttpClient;
import com.rodrilang.librarymanager.media.exception.RemoteImageDownloadException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class GoogleDriveImageDownloader
        implements RemoteImageDownloader {

    private final GoogleDriveUrlParser urlParser;
    private final RemoteImageHttpClient httpClient;

    @Override
    public boolean supports(String sourceUrl) {
        return urlParser.supports(sourceUrl);
    }

    @Override
    public DownloadedImage download(String sourceUrl) {
        String fileId = urlParser.extractFileId(sourceUrl);

        DownloadedImage downloaded = httpClient.download(
                urlParser.buildDownloadUrl(sourceUrl),
                "drive-" + fileId
        );

        validateDriveResponse(downloaded);

        return new DownloadedImage(
                downloaded.content(),
                downloaded.filename(),
                downloaded.declaredContentType(),
                sourceUrl
        );
    }

    private void validateDriveResponse(
            DownloadedImage downloaded
    ) {
        String contentType =
                downloaded.declaredContentType();

        if (
                contentType != null
                        && (
                        contentType
                                .toLowerCase(Locale.ROOT)
                                .contains("text/html")
                                || contentType
                                .toLowerCase(Locale.ROOT)
                                .contains("application/json")
                )
        ) {
            throw new RemoteImageDownloadException(
                    "Google Drive no entregó una imagen. "
                            + "Verificá que el archivo sea público.",
                    false
            );
        }

        if (looksLikeHtml(downloaded.content())) {
            throw new RemoteImageDownloadException(
                    "Google Drive devolvió una página de acceso en lugar de una imagen.",
                    false
            );
        }
    }

    private boolean looksLikeHtml(byte[] content) {
        int length = Math.min(content.length, 512);

        String beginning = new String(
                content,
                0,
                length,
                StandardCharsets.UTF_8
        )
                .trim()
                .toLowerCase(Locale.ROOT);

        return beginning.startsWith("<!doctype html")
                || beginning.startsWith("<html")
                || beginning.contains("<body");
    }
}