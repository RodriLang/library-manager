package com.rodrilang.librarymanager.metadata.cover;

public record CoverCandidate(
        String url,
        String title,
        String source,
        String mime,
        int score
) {
}