package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.metadata.openlibrary.dto.OpenLibraryAuthorDto;
import com.rodrilang.librarymanager.metadata.openlibrary.dto.OpenLibraryBookResponse;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.service.OpenLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpenLibraryServiceImpl implements OpenLibraryService {

    private final RestClient openLibraryRestClient;
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        String key = "ISBN:" + isbn;

        Map<String, OpenLibraryBookResponse> response = openLibraryRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/books")
                        .queryParam("bibkeys", key)
                        .queryParam("format", "json")
                        .queryParam("jscmd", "data")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.containsKey(key)) {
            return Optional.empty();
        }

        OpenLibraryBookResponse bookResponse = response.get(key);

        Publisher publisher = resolvePublisher(bookResponse);

        Set<Author> authors = resolveAuthors(bookResponse);

        Book book = Book.builder()
                .isbn(isbn)
                .title(bookResponse.title())
                .subtitle(bookResponse.subtitle())
                .pageCount(bookResponse.numberOfPages())
                .coverUrl(resolveCoverUrl(bookResponse))
                .publisher(publisher)
                .authors(authors)
                .active(true)
                .build();

        return Optional.of(bookRepository.save(book));
    }

    private Publisher resolvePublisher(OpenLibraryBookResponse response) {
        if (response.publishers() == null || response.publishers().isEmpty()) {
            return null;
        }

        String publisherName = response.publishers().getFirst().name();

        return publisherRepository.findByNameIgnoreCase(publisherName)
                .orElseGet(() -> publisherRepository.save(
                        Publisher.builder()
                                .name(publisherName)
                                .build()
                ));
    }

    private Set<Author> resolveAuthors(OpenLibraryBookResponse response) {
        if (response.authors() == null || response.authors().isEmpty()) {
            return Set.of();
        }

        return response.authors()
                .stream()
                .map(OpenLibraryAuthorDto::name)
                .filter(name -> name != null && !name.isBlank())
                .map(this::resolveAuthor)
                .collect(Collectors.toSet());
    }

    private Author resolveAuthor(String name) {
        return authorRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> authorRepository.save(
                        Author.builder()
                                .name(name)
                                .build()
                ));
    }

    private String resolveCoverUrl(OpenLibraryBookResponse response) {
        if (response.cover() == null) {
            return null;
        }

        if (response.cover().large() != null) {
            return response.cover().large();
        }

        if (response.cover().medium() != null) {
            return response.cover().medium();
        }

        return response.cover().small();
    }
}