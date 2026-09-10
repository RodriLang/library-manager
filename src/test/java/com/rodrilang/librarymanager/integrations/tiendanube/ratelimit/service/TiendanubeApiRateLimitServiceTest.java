package com.rodrilang.librarymanager.integrations.tiendanube.ratelimit.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.ratelimit.repository.TiendanubeApiRateLimitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeApiRateLimitServiceTest {

    @Mock
    private TiendanubeApiRateLimitRepository repository;

    @Test
    void calculatesSingleSlotCooldownFromBucketHeaders() {
        TiendanubeApiRateLimitService service = new TiendanubeApiRateLimitService(repository);
        TiendanubeStore store = TiendanubeStore.builder().id(30L).storeId(3L).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rate-limit-limit", "40");
        headers.set("x-rate-limit-remaining", "0");
        headers.set("x-rate-limit-reset", "20000");

        Duration retryAfter = service.registerRateLimited(store, headers);

        assertTrue(retryAfter.compareTo(Duration.ofMillis(575)) >= 0);
        assertTrue(retryAfter.compareTo(Duration.ofMillis(600)) < 0);
    }

    @Test
    void longPersistedCooldownDoesNotBlockWorkerThread() {
        TiendanubeApiRateLimitService service = new TiendanubeApiRateLimitService(repository);
        TiendanubeStore store = TiendanubeStore.builder().id(30L).storeId(3L).build();
        when(repository.findBlockedUntil(30L, 3L)).thenReturn(Optional.of(Instant.now().plusSeconds(5)));

        TiendanubeApiException exception = assertThrows(TiendanubeApiException.class, () -> service.beforeRequest(store));

        assertEquals(429, exception.getHttpStatus());
        assertEquals("LOCAL_RATE_LIMIT", exception.getRemoteErrorCode());
        assertTrue(exception.getRetryAfter().compareTo(Duration.ofSeconds(1)) > 0);
    }

    @Test
    void persistenceFailureDoesNotMaskRemoteRateLimit() {
        TiendanubeApiRateLimitService service = new TiendanubeApiRateLimitService(repository);
        TiendanubeStore store = TiendanubeStore.builder().id(30L).storeId(3L).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "2");
        doThrow(new RuntimeException("db unavailable")).when(repository)
                .upsert(eq(30L), eq(3L), any(), any(), any(), any());

        Duration retryAfter = service.registerRateLimited(store, headers);

        assertTrue(retryAfter.compareTo(Duration.ofSeconds(2)) > 0);
    }

    @Test
    void nearFullBucketPersistsProactiveCooldown() {
        TiendanubeApiRateLimitService service = new TiendanubeApiRateLimitService(repository);
        TiendanubeStore store = TiendanubeStore.builder().id(30L).storeId(3L).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rate-limit-limit", "40");
        headers.set("x-rate-limit-remaining", "1");
        headers.set("x-rate-limit-reset", "19500");
        ArgumentCaptor<Instant> blockedUntil = ArgumentCaptor.forClass(Instant.class);

        service.registerResponse(store, headers);

        verify(repository).upsert(eq(30L), eq(3L), eq(40), eq(1), eq(19500L), blockedUntil.capture());
        assertNotNull(blockedUntil.getValue());
        assertTrue(blockedUntil.getValue().isAfter(Instant.now()));
    }
}
