package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.AuthorRequest;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.AuthorMapper;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.service.AuthorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;
    private final AuthorMapper authorMapper;

    @Override
    public AuthorResponse create(AuthorRequest request) {

        if (authorRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("El autor ya existe");
        }

        Author author = authorMapper.toEntity(request);

        return authorMapper.toResponse(authorRepository.save(author));
    }

    @Override
    public AuthorResponse findById(Long id) {

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró autor con ID: " + id));

        return authorMapper.toResponse(author);
    }

    @Override
    public Page<AuthorResponse> findAll(Pageable pageable) {

        return authorRepository.findAll(pageable).map(authorMapper::toResponse);
    }

    @Override
    public List<AuthorResponse> search(String query) {

        return authorRepository.findByNameContainingIgnoreCase(query)
                .stream()
                .map(authorMapper::toResponse)
                .toList();
    }

    @Override
    public Author getEntityById(Long id) {

        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró autor con ID: " + id));
    }

    @Override
    public Set<Author> getEntitiesByIds(Set<Long> ids) {

        return new HashSet<>(authorRepository.findAllById(ids));
    }
}