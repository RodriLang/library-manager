package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A JVM restart interrupts in-memory workers; persisted snapshots remain accessible. */
@Component
@RequiredArgsConstructor
public class TiendanubeCategoryAssignmentImportRecovery {
    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedImports() {
        jdbc.update("""
                UPDATE tiendanube_category_assignment_imports
                SET status='FAILED', finished_at=NOW(),
                    error_message='El servidor se reinició durante la importación. Ejecutá un nuevo análisis.'
                WHERE status IN ('SCANNING', 'APPLYING')
                """);
    }
}
