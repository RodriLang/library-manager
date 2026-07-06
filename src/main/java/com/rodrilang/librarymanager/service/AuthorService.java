package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.request.AuthorRequest;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

public interface AuthorService {

    AuthorResponse create(AuthorRequest request);

    AuthorResponse findById(Long id);

    Page<AuthorResponse> findAll(Pageable pageable);

    Page<AuthorResponse> search(String query, Pageable pageable);

    Author getEntityById(Long id);

    Author findOrCreateByName(String name);

    Set<Author> getEntitiesByIds(Set<Long> ids);
}