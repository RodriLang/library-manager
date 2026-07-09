package com.rodrilang.librarymanager.metadata.cover.provider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Order(11)
@Component
public class MatorralCoverProvider extends PrefixImageCoverProvider {

    public MatorralCoverProvider(
            @Qualifier("coverProviderRestClient") RestClient restClient
    ) {
        super(restClient);
    }

    @Override
    protected String buildUrl(String prefix, String isbn) {
        return "https://www.libreriadelgam.cl/imagenes/%s/%s.JPG"
                .formatted(prefix, isbn.substring(0, 12));
    }

    @Override
    protected String mime() {
        return "image/jpeg";
    }

    @Override
    protected int score() {
        return 65;
    }

    @Override
    public String name() {
        return "Librería del GAM";
    }
}