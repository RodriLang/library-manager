package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationSseService {

    private static final long EMITTER_TIMEOUT_MS = 0L;
    private static final long RECONNECT_TIME_MS = 3_000L;

    private final Map<Long, Set<SseEmitter>> emittersByBookstore = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long bookstoreId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByBookstore
                .computeIfAbsent(bookstoreId, ignored -> ConcurrentHashMap.newKeySet())
                .add(emitter);

        Runnable cleanup = () -> remove(bookstoreId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(RECONNECT_TIME_MS)
                    .data(Map.of(
                            "bookstoreId", bookstoreId,
                            "connectedAt", Instant.now().toString()
                    )));
        } catch (IOException | IllegalStateException ex) {
            remove(bookstoreId, emitter);
            log.debug("No se pudo enviar el evento inicial SSE para la librería {}", bookstoreId, ex);
        }

        return emitter;
    }

    public void publish(Long bookstoreId, NotificationResponse notification) {
        Set<SseEmitter> emitters = emittersByBookstore.get(bookstoreId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.id()))
                        .name("notification")
                        .reconnectTime(RECONNECT_TIME_MS)
                        .data(notification));
            } catch (IOException | IllegalStateException ex) {
                remove(bookstoreId, emitter);
                log.debug("Se eliminó un stream SSE cerrado para la librería {}", bookstoreId, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notifications.sse-heartbeat-ms:25000}")
    public void heartbeat() {
        if (emittersByBookstore.isEmpty()) return;

        emittersByBookstore.forEach((bookstoreId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("at", Instant.now().toString())));
                } catch (IOException | IllegalStateException ex) {
                    remove(bookstoreId, emitter);
                    log.debug("Heartbeat SSE descartó una conexión cerrada para la librería {}", bookstoreId, ex);
                }
            }
        });
    }

    int connectionCount(Long bookstoreId) {
        Set<SseEmitter> emitters = emittersByBookstore.get(bookstoreId);
        return emitters == null ? 0 : emitters.size();
    }

    private void remove(Long bookstoreId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByBookstore.get(bookstoreId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) emittersByBookstore.remove(bookstoreId, emitters);
    }

}
