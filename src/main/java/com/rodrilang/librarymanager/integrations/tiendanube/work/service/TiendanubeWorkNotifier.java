package com.rodrilang.librarymanager.integrations.tiendanube.work.service;

import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.event.TiendanubeWorkAvailableEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TiendanubeWorkNotifier {

    private final ApplicationEventPublisher eventPublisher;

    public void notifyWork(TiendanubeWorkType type) {
        eventPublisher.publishEvent(new TiendanubeWorkAvailableEvent(type));
    }
}
