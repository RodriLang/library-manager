package com.rodrilang.librarymanager.cover.job.service;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;
import com.rodrilang.librarymanager.cover.job.configuration.BookCoverJobProperties;
import com.rodrilang.librarymanager.cover.job.entity.BookCoverJob;
import com.rodrilang.librarymanager.cover.job.repository.BookCoverJobRepository;
import com.rodrilang.librarymanager.cover.job.request.CreateBookCoverJobRequest;
import com.rodrilang.librarymanager.cover.job.response.CreateBookCoverJobResult;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.media.download.RemoteImageUrlNormalizer;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCoverJobServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookCoverJobRepository jobRepository;

    @Mock
    private PriceListImportJobRepository priceListImportJobRepository;

    @Mock
    private RemoteImageUrlNormalizer urlNormalizer;

    @Mock
    private BookCoverJobKeyService jobKeyService;

    @Mock
    private BookCoverJobProperties properties;

    @InjectMocks
    private BookCoverJobService service;

    @Test
    void shouldReturnExistingJobWhenSameBookAndUrlAlreadyExists() {
        Long bookId = 3108L;
        Long existingJobId = 25L;

        String sourceUrl =
                "https://drive.google.com/file/d/abc/view";

        String normalizedUrl =
                "https://drive.google.com/file/d/abc/view";

        String jobKey =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        CreateBookCoverJobRequest request =
                new CreateBookCoverJobRequest(
                        bookId,
                        null,
                        sourceUrl,
                        BookCoverSource.PRICE_LIST,
                        15
                );

        BookCoverJob existingJob = mock(BookCoverJob.class);

        when(urlNormalizer.normalize(sourceUrl))
                .thenReturn(normalizedUrl);

        when(jobKeyService.generate(bookId, normalizedUrl))
                .thenReturn(jobKey);

        when(existingJob.getId())
                .thenReturn(existingJobId);

        when(jobRepository.findByJobKey(jobKey))
                .thenReturn(Optional.of(existingJob));

        CreateBookCoverJobResult result = service.create(request);

        assertThat(result.created()).isFalse();
        assertThat(result.jobId()).isEqualTo(existingJobId);
        assertThat(result.reason()).isEqualTo("DUPLICATE");

        verify(jobRepository).findByJobKey(jobKey);
        verify(jobRepository, never()).saveAndFlush(any());
        verifyNoInteractions(bookRepository);
    }

    @Test
    void shouldCreateJobWhenItDoesNotExist() {
        Long bookId = 3108L;
        Long createdJobId = 25L;

        String sourceUrl =
                "https://drive.google.com/file/d/abc/view";

        String normalizedUrl =
                "https://drive.google.com/file/d/abc/view";

        String jobKey =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        CreateBookCoverJobRequest request =
                new CreateBookCoverJobRequest(
                        bookId,
                        null,
                        sourceUrl,
                        BookCoverSource.PRICE_LIST,
                        15
                );

        Book book = Book.builder()
                .id(bookId)
                .title("Libro de prueba")
                .build();

        when(urlNormalizer.normalize(sourceUrl))
                .thenReturn(normalizedUrl);

        when(jobKeyService.generate(bookId, normalizedUrl))
                .thenReturn(jobKey);

        when(jobRepository.findByJobKey(jobKey))
                .thenReturn(Optional.empty());

        when(bookRepository.findById(bookId))
                .thenReturn(Optional.of(book));

        when(properties.maxAttempts())
                .thenReturn(4);

        when(jobRepository.saveAndFlush(any(BookCoverJob.class)))
                .thenAnswer(invocation -> {
                    BookCoverJob job = invocation.getArgument(0);

                    // Como el id no tiene setter, usamos un mock como resultado persistido.
                    BookCoverJob savedJob = mock(BookCoverJob.class);
                    when(savedJob.getId()).thenReturn(createdJobId);

                    return savedJob;
                });

        CreateBookCoverJobResult result = service.create(request);

        assertThat(result.created()).isTrue();
        assertThat(result.jobId()).isEqualTo(createdJobId);
        assertThat(result.reason()).isNull();

        verify(jobRepository).saveAndFlush(any(BookCoverJob.class));
    }
}