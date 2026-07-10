package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.util.Optional;

public abstract class DirectUrlCoverProvider implements CoverProvider {

    protected final RestClient restClient;

    protected DirectUrlCoverProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    protected Optional<CoverCandidate> buildCandidate(
            String url,
            String source,
            String title,
            String mime,
            int score
    ) {
        if (!exists(url)) {
            return Optional.empty();
        }

        return Optional.of(new CoverCandidate(
                url,
                title,
                source,
                mime,
                score
        ));
    }

    private boolean exists(String url) {
        try {
            return restClient.method(HttpMethod.HEAD)
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception ex) {
            return false;
        }
    }
}