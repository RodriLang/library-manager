package com.rodrilang.librarymanager.metadata.cover.provider;

import com.rodrilang.librarymanager.metadata.cover.CoverCandidate;
import com.rodrilang.librarymanager.model.Book;

import java.util.Optional;

public interface CoverProvider {

    Optional<CoverCandidate> findCover(Book book);

    String name();

}