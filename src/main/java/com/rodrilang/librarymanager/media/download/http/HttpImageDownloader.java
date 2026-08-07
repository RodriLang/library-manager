package com.rodrilang.librarymanager.media.download.http;

import com.rodrilang.librarymanager.media.download.DownloadedImage;
import com.rodrilang.librarymanager.media.download.RemoteImageDownloader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class HttpImageDownloader
        implements RemoteImageDownloader {

    private final RemoteImageHttpClient httpClient;

    @Override
    public boolean supports(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(sourceUrl.trim());

            String scheme = uri.getScheme();

            if (scheme == null || uri.getHost() == null) {
                return false;
            }

            String normalizedScheme =
                    scheme.toLowerCase(Locale.ROOT);

            return normalizedScheme.equals("http")
                    || normalizedScheme.equals("https");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public DownloadedImage download(String sourceUrl) {
        return httpClient.download(
                sourceUrl.trim(),
                "remote-cover"
        );
    }
}