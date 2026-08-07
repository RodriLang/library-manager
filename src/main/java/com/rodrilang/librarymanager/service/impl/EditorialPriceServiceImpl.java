package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceInsertRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceUpdateRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.EditorialPriceBatchRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EditorialPriceServiceImpl implements EditorialPriceService {

    private final EditorialPriceRepository editorialPriceRepository;
    private final EditorialPriceBatchRepository batchRepository;
    private final PriceListImportJobRepository jobRepository;

    @Override
    @Transactional
    public PriceImportCounters registerResolvedPrices(
            Long jobId,
            List<PriceListResolvedPrice> prices
    ) {
        if (prices == null || prices.isEmpty()) {
            return new PriceImportCounters(
                    0,
                    0,
                    0,
                    0
            );
        }

        PriceListImportJob job = jobRepository
                .findById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

        List<Long> bookIds = prices.stream()
                .map(PriceListResolvedPrice::bookId)
                .distinct()
                .toList();

        Map<Long, EditorialPrice> existingByBookId =
                editorialPriceRepository
                        .findByBookIdInAndProviderIdAndValidFrom(
                                bookIds,
                                job.getProvider().getId(),
                                job.getValidFrom()
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        price -> price.getBook().getId(),
                                        Function.identity()
                                )
                        );

        List<EditorialPriceInsertRow> toInsert = new ArrayList<>();

        List<EditorialPriceUpdateRow> toUpdate = new ArrayList<>();

        int unchanged = 0;

        for (PriceListResolvedPrice resolved : prices) {

            EditorialPrice existing = existingByBookId.get(resolved.bookId());

            if (existing == null) {
                toInsert.add(
                        new EditorialPriceInsertRow(
                                resolved.bookId(),
                                resolved.selectedPrice()
                        )
                );

                continue;
            }

            if (existing.getPrice().compareTo(resolved.selectedPrice()) == 0) {
                unchanged++;
                continue;
            }

            toUpdate.add(
                    new EditorialPriceUpdateRow(
                            existing.getId(),
                            resolved.selectedPrice()
                    )
            );
        }

        batchRepository.insertBatch(
                job.getProvider().getId(),
                job.getValidFrom(),
                toInsert
        );

        batchRepository.updateBatch(toUpdate);

        return new PriceImportCounters(
                toInsert.size(),
                toUpdate.size(),
                unchanged,
                0
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<EditorialPrice> findCurrentByBookId(Long bookId) {
        return editorialPriceRepository
                .findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                        bookId,
                        LocalDate.now(ZoneId.systemDefault())
                );
    }
}
