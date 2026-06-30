package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.request.PublisherRequest;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.model.Publisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PublisherService {

    PublisherResponse create(PublisherRequest request);

    PublisherResponse findById(Long id);

    Page<PublisherResponse> findAll(Pageable pageable);

    List<PublisherResponse> search(String query);

    Publisher getEntityById(Long id);
}
