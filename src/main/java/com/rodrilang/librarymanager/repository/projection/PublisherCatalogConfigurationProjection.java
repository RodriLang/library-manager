package com.rodrilang.librarymanager.repository.projection;

public interface PublisherCatalogConfigurationProjection {

    Long getId();

    String getName();

    long getBookCount();

    boolean getExcluded();
}