package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class TiendanubeJobExecutor {

    private final Map<TiendanubeJobType, TiendanubeJobHandler> handlers;

    public TiendanubeJobExecutor(List<TiendanubeJobHandler> handlers) {
        this.handlers = new EnumMap<>(TiendanubeJobType.class);

        for (TiendanubeJobHandler handler : handlers) {
            TiendanubeJobHandler previous = this.handlers.put(handler.type(), handler);

            if (previous != null) {
                throw new IllegalStateException("Hay más de un handler registrado para el job de Tiendanube " + handler.type());
            }
        }
    }

    public void execute(TiendanubeJobExecutionContext context) {
        TiendanubeJobHandler handler = handlers.get(context.type());

        if (handler == null) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "HANDLER_NOT_FOUND",
                    "No hay un handler registrado para el job de Tiendanube " + context.type(),
                    null
            );
        }

        handler.execute(context);
    }
}
