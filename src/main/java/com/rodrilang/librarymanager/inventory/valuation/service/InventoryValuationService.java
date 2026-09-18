package com.rodrilang.librarymanager.inventory.valuation.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.valuation.dto.response.InventoryValuationResponse;
import com.rodrilang.librarymanager.inventory.valuation.repository.InventoryValuationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class InventoryValuationService {

    private static final ZoneId ANAQUEL_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final InventoryValuationRepository repository;
    private final InventoryValuationCalculator calculator;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public InventoryValuationResponse getCurrent() {
        LocalDate today = LocalDate.now(ANAQUEL_ZONE);
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return calculator.calculate(
                today,
                repository.summarize(bookstoreId, today)
        );
    }
}
