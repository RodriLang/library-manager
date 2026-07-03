package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.AuthorRequest;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.AuthorMapper;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.service.AuthorService;
import com.rodrilang.librarymanager.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;
    private final AuthorMapper authorMapper;

    @Transactional
    @Override
    public AuthorResponse create(AuthorRequest request) {

        String name = StringUtils.normalizeName(request.name());

        if (authorRepository.existsNormalized(name)) {
            throw new DuplicateResourceException("El autor ya existe");
        }

        Author author = authorMapper.toEntity(new AuthorRequest(name));

        return authorMapper.toResponse(authorRepository.save(author));
    }

    @Transactional(readOnly = true)
    @Override
    public AuthorResponse findById(Long id) {

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró autor con ID: " + id));

        return authorMapper.toResponse(author);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AuthorResponse> findAll(Pageable pageable) {

        return authorRepository.findAll(pageable).map(authorMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AuthorResponse> search(String query) {

        return authorRepository.findByNameContainingIgnoreCase(query)
                .stream()
                .map(authorMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Author getEntityById(Long id) {

        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró autor con ID: " + id));
    }

    @Override
    @Transactional
    public Author findOrCreateByName(String name) {

        if (name == null || name.isBlank()) {
            return null;
        }

        return authorRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> authorRepository.save(
                        Author.builder()
                                .name(name.trim())
                                .build()
                ));
    }

    @Transactional(readOnly = true)
    @Override
    public Set<Author> getEntitiesByIds(Set<Long> ids) {

        return new HashSet<>(authorRepository.findAllById(ids));
    }
}