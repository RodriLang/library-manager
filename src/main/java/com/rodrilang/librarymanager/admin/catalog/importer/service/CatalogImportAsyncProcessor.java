package com.rodrilang.librarymanager.admin.catalog.importer.service;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportJob;
import com.rodrilang.librarymanager.admin.catalog.importer.repository.CatalogImportJobRepository;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogImportAsyncProcessor {
    private static final int BATCH_SIZE = 150;
    private final CatalogImportJobRepository jobRepository;
    private final CatalogImportFormatService formatService;
    private final StreamingConfigurablePriceListParser parser;
    private final CatalogImportBatchService batchService;
    private final CatalogImportProgressService progressService;
    private final PriceListImportFileStorage fileStorage;

    @Async("catalogImportExecutor")
    public void process(Long jobId) {
        Path filePath=null;
        try {
            CatalogImportJob job=jobRepository.findDetailedById(jobId).orElseThrow();
            filePath=Path.of(job.getTemporaryFilePath());
            progressService.markProcessing(jobId);
            var parserConfig=formatService.toParserConfig(job.getFormat().getId());
            AtomicInteger totalRows = new AtomicInteger();
            parser.parse(filePath, parserConfig, row -> { if (!isEmpty(row)) totalRows.incrementAndGet(); });
            progressService.setTotalRows(jobId, totalRows.get());

            List<PriceListRow> batch=new ArrayList<>(BATCH_SIZE);
            parser.parse(filePath, parserConfig, row -> {
                if (isEmpty(row)) return;
                if (progressService.cancellationRequested(jobId)) throw new CatalogImportCancelledException();
                batch.add(row);
                if (batch.size()>=BATCH_SIZE) flush(jobId,batch);
            });
            flush(jobId,batch);
            if (progressService.cancellationRequested(jobId)) progressService.cancelled(jobId); else progressService.complete(jobId);
        } catch (CatalogImportCancelledException ex) {
            progressService.cancelled(jobId);
        } catch (Exception ex) {
            log.error("Catalog import failed. jobId={}",jobId,ex);
            progressService.fail(jobId, ex.getMessage()==null?"No se pudo procesar la importación.":ex.getMessage());
        } finally {
            if (filePath!=null) fileStorage.deleteQuietly(filePath);
        }
    }

    private boolean isEmpty(PriceListRow row) {
        if (row == null) return true;
        var m = row.metadata();
        return blank(row.isbn()) && blank(row.title()) && blank(row.authorName()) && blank(row.publisherName())
                && blank(row.categoryName()) && row.retailPrice() == null
                && (m == null || (blank(m.externalCode()) && blank(m.subtitle()) && blank(m.description())
                && blank(m.genreName()) && blank(m.collectionName()) && blank(m.language()) && blank(m.sourceCoverUrl())
                && m.pageCount() == null && m.publicationYear() == null && m.widthCm() == null && m.heightCm() == null
                && m.depthCm() == null && m.weightGrams() == null));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private void flush(Long jobId,List<PriceListRow> batch){
        if(batch.isEmpty()) return;
        CatalogImportJob job=jobRepository.findDetailedById(jobId).orElseThrow();
        CatalogImportBatchResult result=batchService.process(job,List.copyOf(batch));
        progressService.add(jobId,result); batch.clear();
    }

    private static class CatalogImportCancelledException extends RuntimeException {}
}
