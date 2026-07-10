package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookstoreServiceImpl implements BookstoreService {

    private final BookstoreRepository bookstoreRepository;

    @Transactional(readOnly = true)
    @Override
    public Bookstore getEntityById(Long id) {
        return bookstoreRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("No se encontró la librería con ID: " + id));
    }
}