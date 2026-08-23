package com.rodrilang.librarymanager.repository.projection;

public interface PublisherConfigurationDetailProjection {

    Long getId();

    String getName();

    long getBookCount();

    boolean getExcluded();
}