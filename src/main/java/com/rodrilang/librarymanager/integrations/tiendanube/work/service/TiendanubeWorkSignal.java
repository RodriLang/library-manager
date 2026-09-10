package com.rodrilang.librarymanager.integrations.tiendanube.work.service;

import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TiendanubeWorkSignal {

    private final Map<TiendanubeWorkType, WorkState> states = new EnumMap<>(TiendanubeWorkType.class);

    public TiendanubeWorkSignal() {
        for (TiendanubeWorkType type : TiendanubeWorkType.values()) {
            states.put(type, new WorkState());
        }
    }

    public void wakeNow(TiendanubeWorkType type) {
        state(type).immediate.set(true);
    }

    public void scheduleAt(TiendanubeWorkType type, Instant instant) {
        if (instant == null) {
            return;
        }

        AtomicReference<Instant> scheduledAt = state(type).scheduledAt;
        scheduledAt.updateAndGet(current -> current == null || instant.isBefore(current) ? instant : current);
    }

    public void replaceScheduledAt(TiendanubeWorkType type, Optional<Instant> instant) {
        state(type).scheduledAt.set(instant.orElse(null));
    }

    public boolean shouldRun(TiendanubeWorkType type) {
        WorkState state = state(type);

        if (state.immediate.getAndSet(false)) {
            return true;
        }

        Instant scheduledAt = state.scheduledAt.get();
        if (scheduledAt == null || scheduledAt.isAfter(Instant.now())) {
            return false;
        }

        return state.scheduledAt.compareAndSet(scheduledAt, null);
    }

    private WorkState state(TiendanubeWorkType type) {
        WorkState state = states.get(type);
        if (state == null) {
            throw new IllegalArgumentException("Tipo de trabajo Tiendanube no soportado: " + type);
        }
        return state;
    }

    private static final class WorkState {
        private final AtomicBoolean immediate = new AtomicBoolean();
        private final AtomicReference<Instant> scheduledAt = new AtomicReference<>();
    }
}
