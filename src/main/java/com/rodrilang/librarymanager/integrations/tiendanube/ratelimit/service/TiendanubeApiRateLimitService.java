package com.rodrilang.librarymanager.integrations.tiendanube.ratelimit.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiErrorKind;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.ratelimit.repository.TiendanubeApiRateLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeApiRateLimitService {

    private static final String LIMIT_HEADER = "x-rate-limit-limit";
    private static final String REMAINING_HEADER = "x-rate-limit-remaining";
    private static final String RESET_HEADER = "x-rate-limit-reset";
    private static final int SAFETY_REMAINING = 2;
    private static final long SAFETY_MILLIS = 75;
    private static final Duration MAX_INLINE_WAIT = Duration.ofSeconds(1);

    private final TiendanubeApiRateLimitRepository repository;

    public void beforeRequest(TiendanubeStore store) {
        Instant blockedUntil;

        try {
            blockedUntil = repository.findBlockedUntil(store.getId(), store.getStoreId()).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("No se pudo consultar el rate limit persistido de Tiendanube. storeId={}",
                    store.getStoreId(), exception);
            return;
        }

        if (blockedUntil == null) {
            return;
        }

        Duration wait = Duration.between(Instant.now(), blockedUntil);

        if (wait.isNegative() || wait.isZero()) {
            return;
        }

        if (wait.compareTo(MAX_INLINE_WAIT) > 0) {
            throw localRateLimitException(wait, null);
        }

        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw localRateLimitException(wait, exception);
        }
    }

    public void registerResponse(TiendanubeStore store, HttpHeaders headers) {
        RateLimitHeaders rateLimit = parse(headers);

        if (!rateLimit.hasAnyValue()) {
            return;
        }

        Instant blockedUntil = calculateProactiveBlock(rateLimit);
        persistState(store, rateLimit, blockedUntil);
    }

    public Duration registerRateLimited(TiendanubeStore store, HttpHeaders headers) {
        RateLimitHeaders rateLimit = parse(headers);
        Duration retryAfter = parseRetryAfter(headers);

        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            retryAfter = calculateSingleSlotDelay(rateLimit);
        }

        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            retryAfter = Duration.ofSeconds(1);
        }

        retryAfter = retryAfter.plusMillis(SAFETY_MILLIS);
        Instant blockedUntil = Instant.now().plus(retryAfter);

        persistState(store, rateLimit, blockedUntil);

        log.warn("Tiendanube rate limit reached. storeId={} retryAfterMs={}", store.getStoreId(), retryAfter.toMillis());
        return retryAfter;
    }

    private void persistState(TiendanubeStore store, RateLimitHeaders rateLimit, Instant blockedUntil) {
        try {
            repository.upsert(
                    store.getId(), store.getStoreId(), rateLimit.limit(), rateLimit.remaining(),
                    rateLimit.resetAfterMs(), blockedUntil
            );
        } catch (RuntimeException exception) {
            log.warn("No se pudo persistir el rate limit de Tiendanube. storeId={}", store.getStoreId(), exception);
        }
    }

    private TiendanubeApiException localRateLimitException(Duration retryAfter, Throwable cause) {
        return new TiendanubeApiException(
                "Tiendanube tiene una ventana de rate limit activa para esta tienda",
                cause,
                "respetar rate limit",
                429,
                "LOCAL_RATE_LIMIT",
                TiendanubeApiErrorKind.RATE_LIMIT,
                retryAfter
        );
    }

    private Instant calculateProactiveBlock(RateLimitHeaders rateLimit) {
        if (rateLimit.remaining() == null || rateLimit.remaining() > SAFETY_REMAINING) {
            return null;
        }

        Duration delay = calculateSingleSlotDelay(rateLimit);
        return delay == null ? null : Instant.now().plus(delay).plusMillis(SAFETY_MILLIS);
    }

    private Duration calculateSingleSlotDelay(RateLimitHeaders rateLimit) {
        if (rateLimit.resetAfterMs() == null || rateLimit.resetAfterMs() <= 0) {
            return null;
        }

        if (rateLimit.limit() == null || rateLimit.remaining() == null) {
            return Duration.ofMillis(Math.min(rateLimit.resetAfterMs(), 1000L));
        }

        int used = Math.max(1, rateLimit.limit() - rateLimit.remaining());
        long millisPerSlot = Math.max(1L, (long) Math.ceil((double) rateLimit.resetAfterMs() / used));
        return Duration.ofMillis(millisPerSlot);
    }

    private RateLimitHeaders parse(HttpHeaders headers) {
        if (headers == null) {
            return RateLimitHeaders.empty();
        }

        return new RateLimitHeaders(
                parseInteger(headers.getFirst(LIMIT_HEADER)),
                parseInteger(headers.getFirst(REMAINING_HEADER)),
                parseLong(headers.getFirst(RESET_HEADER))
        );
    }

    public Duration resolveRetryAfter(HttpHeaders headers) {
        return parseRetryAfter(headers);
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }

        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Duration.between(Instant.now(), retryAt);
            } catch (RuntimeException exception) {
                log.debug("No se pudo interpretar Retry-After de Tiendanube: {}", value);
                return null;
            }
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record RateLimitHeaders(Integer limit, Integer remaining, Long resetAfterMs) {

        static RateLimitHeaders empty() {
            return new RateLimitHeaders(null, null, null);
        }

        boolean hasAnyValue() {
            return limit != null || remaining != null || resetAfterMs != null;
        }
    }
}
