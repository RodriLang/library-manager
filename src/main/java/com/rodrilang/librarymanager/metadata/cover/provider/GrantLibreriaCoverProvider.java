package com.rodrilang.librarymanager.metadata.cover.provider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Order(14)
@Component
public class GrantLibreriaCoverProvider extends PrefixImageCoverProvider {

    public GrantLibreriaCoverProvider(
            @Qualifier("coverProviderRestClient") RestClient restClient
    ) {
        super(restClient);
    }

    @Override
    protected String buildUrl(String prefix, String isbn) {
        return "https://www.grantlibreria.com/imagenes/%s/%s.GIF"
                .formatted(prefix, isbn.substring(0, 12));
    }

    @Override
    protected String mime() {
        return "image/gif";
    }

    @Override
    protected int score() {
        return 62;
    }

    @Override
    public String name() {
        return "Grant Librería";
    }
}