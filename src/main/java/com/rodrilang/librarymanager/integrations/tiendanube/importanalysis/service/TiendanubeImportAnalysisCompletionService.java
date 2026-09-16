package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeClaimedImportAnalysisRun;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemData;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisItemWriteRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisCompletionService {

    private final TiendanubeImportAnalysisItemWriteRepository itemWriteRepository;
    private final TiendanubeImportAnalysisRunRepository runRepository;
    private final TiendanubeWorkNotifier workNotifier;

    @Transactional
    public void complete(
            TiendanubeClaimedImportAnalysisRun run,
            List<TiendanubeImportAnalysisItemData> items
    ) {
        Instant now = Instant.now();
        itemWriteRepository.replaceAnalysis(run.id(), items, now);

        if (!runRepository.markCompleted(run.id(), run.processingToken(), now)) {
            throw new IllegalStateException("Se perdió el lease del análisis Tiendanube antes de completarlo");
        }

        workNotifier.notifyWork(TiendanubeWorkType.IMPORT_ANALYSIS);
    }

    @Transactional
    public void fail(TiendanubeClaimedImportAnalysisRun run, RuntimeException exception) {
        String type = exception.getClass().getSimpleName();
        String message = exception.getMessage();

        runRepository.markFailed(
                run.id(),
                run.processingToken(),
                type,
                message == null ? type : message,
                Instant.now()
        );

        workNotifier.notifyWork(TiendanubeWorkType.IMPORT_ANALYSIS);
    }
}
