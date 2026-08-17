package com.rodrilang.librarymanager.purchasing.provider.repository.projection;

public interface BookAlternativeProviderProjection {

    Long getBookId();

    Long getProviderId();

    String getProviderName();
}