package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.request.PublisherRequest;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.mapper.PublisherMapper;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.service.PublisherService;
import com.rodrilang.librarymanager.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublisherServiceImpl implements PublisherService {

    private final PublisherRepository publisherRepository;
    private final PublisherMapper publisherMapper;

    @Transactional
    @Override
    public PublisherResponse create(PublisherRequest request) {

        String name = StringUtils.normalizeName(request.name());

        if (publisherRepository.existsNormalized(name)) {
            throw new DuplicateResourceException("La editorial ya existe");
        }

        Publisher publisher = publisherMapper.toEntity(new PublisherRequest(name));

        return publisherMapper.toResponse(
                publisherRepository.save(publisher)
        );
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

        return publisherRepository.findAll(pageable)
                .map(publisherMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PublisherResponse> search(String query) {

        return publisherRepository
                .findByNameContainingIgnoreCase(query)
                .stream()
                .map(publisherMapper::toResponse)
                .toList();
    }

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

        return publisherRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> publisherRepository.save(
                        Publisher.builder()
                                .name(name.trim())
                                .build()
                ));
    }

}