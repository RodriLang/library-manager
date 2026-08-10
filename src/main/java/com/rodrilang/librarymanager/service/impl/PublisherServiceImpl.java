package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.PublisherRequest;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.PublisherMapper;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.service.PublisherService;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublisherServiceImpl implements PublisherService {

    private final PublisherRepository publisherRepository;
    private final PublisherMapper publisherMapper;

    @Transactional
    @Override
    public PublisherResponse create(PublisherRequest request) {
        String name = normalizeDisplayName(request.name());

        String nameNormalized = TextNormalizer.normalizeForMatch(name);

        if (publisherRepository.existsByNameNormalized(nameNormalized)) {
            throw new DuplicateResourceException("La editorial ya existe");
        }

        Publisher publisher = publisherMapper.toEntity(new PublisherRequest(name));

        return publisherMapper.toResponse(publisherRepository.save(publisher));
    }

    @Transactional(readOnly = true)
    @Override
    public PublisherResponse findById(Long id) {

        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró editorial con ID: " + id));

        return publisherMapper.toResponse(publisher);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PublisherResponse> findAll(Pageable pageable) {

        return publisherRepository.findAll(pageable).map(publisherMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PublisherResponse> search(String query, Pageable pageable) {

        return publisherRepository
                .searchByName(query, pageable)
                .map(publisherMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Publisher getEntityById(Long id) {

        return publisherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró editorial con ID: " + id));
    }

    @Override
    @Transactional
    public Publisher findOrCreateByName(String name) {

        if (name == null || name.isBlank()) {
            return null;
        }

        String displayName = normalizeDisplayName(name);

        String nameNormalized = TextNormalizer.normalizeForMatch(displayName);

        return publisherRepository.findByNameNormalized(nameNormalized)
                .orElseGet(() -> publisherRepository.save(Publisher.builder().name(displayName).build()));
    }

    private String normalizeDisplayName(String value) {
        return value == null
                ? null
                : value.trim()
                .replaceAll("\\s+", " ");
    }
}