package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.inventory.pricing.dto.InventoryPricePointResponse;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryPriceService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final InventoryPriceRepository priceRepository;
    private final InventoryRepository inventoryRepository;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    public LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    @Transactional
    public InventoryPricePointResponse upsertManual(Long inventoryId, BigDecimal amount, LocalDate effectiveFrom) {
        if (effectiveFrom.isBefore(today())) {
            throw new BusinessException("Un nuevo precio no puede comenzar a regir en una fecha pasada.");
        }
        Inventory inventory = getCurrentBookstoreInventory(inventoryId);
        InventoryPrice price = upsert(
                inventory,
                amount,
                effectiveFrom,
                InventoryPriceSource.MANUAL,
                null,
                bookstoreContext.getCurrentUserId(),
                true
        );
        return toResponse(price);
    }

    @Transactional
    public InventoryPrice upsertImported(
            Inventory inventory,
            BigDecimal amount,
            LocalDate effectiveFrom,
            InventoryPriceImport priceImport,
            Long userId
    ) {
        InventoryPrice price = upsert(
                inventory,
                amount,
                effectiveFrom,
                InventoryPriceSource.PRICE_LIST,
                priceImport,
                userId,
                true
        );

        if (price.getLastConfirmedAt() == null || !effectiveFrom.isBefore(price.getLastConfirmedAt())) {
            price.setLastConfirmedAt(effectiveFrom);
            price.setLastConfirmedSource(normalizeConfirmationSource(priceImport != null
                            ? priceImport.getSourceName()
                            : null
                    )
            );
        }

        return price;
    }

    @Transactional
    public InventoryPrice upsertSystem(
            Inventory inventory,
            BigDecimal amount,
            LocalDate effectiveFrom,
            InventoryPriceSource source,
            Long userId
    ) {
        return upsert(inventory, amount, effectiveFrom, source, null, userId, true);
    }

    @Transactional
    public void confirmPrice(
            Inventory inventory,
            BigDecimal confirmedAmount,
            LocalDate confirmedAt,
            String confirmedSource
    ) {
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        validateAmount(confirmedAmount);

        InventoryPrice price = priceRepository
                .findCurrentCandidates(inventory.getId(), confirmedAt)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("No existe un precio vigente para confirmar en la fecha indicada."));

        if (price.getAmount().compareTo(confirmedAmount) != 0) {
            throw new BusinessException("El precio vigente no coincide con el precio que se intenta confirmar.");
        }

        if (price.getLastConfirmedAt() == null || !confirmedAt.isBefore(price.getLastConfirmedAt())) {
            price.setLastConfirmedAt(confirmedAt);
            price.setLastConfirmedSource(normalizeConfirmationSource(confirmedSource));
        }

        inventory.setLastPriceCheckedAt(today());
    }

    @Transactional(readOnly = true)
    public Optional<InventoryPrice> current(Long inventoryId) {
        return priceRepository.findCurrentCandidates(inventoryId, today()).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public BigDecimal currentAmount(Long inventoryId) {
        return current(inventoryId).map(InventoryPrice::getAmount).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryPrice> next(Long inventoryId) {
        return priceRepository.findFuturePrices(inventoryId, today()).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<InventoryPricePointResponse> history(Long inventoryId) {
        Inventory inventory = getCurrentBookstoreInventory(inventoryId);
        return priceRepository.findAllByInventoryIdOrderByEffectiveFromDescIdDesc(inventory.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteFuture(Long inventoryId, Long priceId) {
        Inventory inventory = getCurrentBookstoreInventory(inventoryId);
        InventoryPrice price = priceRepository.findById(priceId)
                .filter(candidate -> candidate.getInventory().getId().equals(inventory.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el precio programado."));
        if (!price.getEffectiveFrom().isAfter(today())) {
            throw new BusinessException("Sólo se pueden cancelar precios que todavía no entraron en vigencia.");
        }
        priceRepository.delete(price);
    }

    @Transactional(readOnly = true)
    public Map<Long, InventoryPrice> currentFor(Collection<Long> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InventoryPrice> result = new LinkedHashMap<>();
        for (InventoryPrice price : priceRepository.findCurrentCandidatesForInventoryIds(inventoryIds, today())) {
            result.putIfAbsent(price.getInventory().getId(), price);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, InventoryPrice> nextFor(Collection<Long> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InventoryPrice> result = new LinkedHashMap<>();
        for (InventoryPrice price : priceRepository.findFuturePricesForInventoryIds(inventoryIds, today())) {
            result.putIfAbsent(price.getInventory().getId(), price);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<InventoryPrice> priceAt(Long inventoryId, LocalDate date) {
        return priceRepository.findCurrentCandidates(inventoryId, date).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Map<Long, InventoryPrice> pricesAt(Collection<Long> inventoryIds, LocalDate date) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return Map.of();
        }

        Objects.requireNonNull(date, "date");

        Map<Long, InventoryPrice> result = new LinkedHashMap<>();

        for (InventoryPrice price : priceRepository.findCurrentCandidatesForInventoryIds(inventoryIds, date)) {

            result.putIfAbsent(price.getInventory().getId(), price);
        }

        return result;
    }

    /**
     * Prices are now resolved directly from inventory_prices.  This scheduler only
     * notifies external integrations when a scheduled price becomes effective;
     * it never copies the value back into inventory.
     */
    @Scheduled(cron = "0 2 0 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional(readOnly = true)
    public void publishScheduledPricesThatBecameEffective() {
        for (InventoryPrice price : priceRepository.findAllByEffectiveFromWithInventory(today())) {
            Inventory inventory = price.getInventory();
            if (inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED
                    && Boolean.TRUE.equals(inventory.getTiendanubePriceSyncEnabled())) {
                eventPublisher.publishEvent(
                        new TiendanubeSyncRequestedEvent(inventory.getId(), TiendanubeSyncType.PRICE)
                );
            }
        }
    }

    private InventoryPrice upsert(
            Inventory inventory,
            BigDecimal amount,
            LocalDate effectiveFrom,
            InventoryPriceSource source,
            InventoryPriceImport priceImport,
            Long userId,
            boolean markChecked
    ) {
        validateAmount(amount);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");

        BigDecimal previousCurrent = currentAmount(inventory.getId());

        InventoryPrice price = priceRepository.findByInventoryIdAndEffectiveFrom(inventory.getId(), effectiveFrom)
                .orElseGet(() -> InventoryPrice.builder()
                        .inventory(inventory)
                        .effectiveFrom(effectiveFrom)
                        .build());

        price.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        price.setSource(source);
        price.setPriceImport(priceImport);
        price.setCreatedByUserId(userId);
        InventoryPrice saved = priceRepository.save(price);

        if (markChecked) {
            inventory.setLastPriceCheckedAt(today());
        }

        if (!effectiveFrom.isAfter(today())) {
            BigDecimal newCurrent = currentAmount(inventory.getId());
            if (!Objects.equals(previousCurrent, newCurrent)
                    && inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED
                    && Boolean.TRUE.equals(inventory.getTiendanubePriceSyncEnabled())) {
                eventPublisher.publishEvent(
                        new TiendanubeSyncRequestedEvent(inventory.getId(), TiendanubeSyncType.PRICE)
                );
            }
        }
        return saved;
    }

    private Inventory getCurrentBookstoreInventory(Long inventoryId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return inventoryRepository.findByIdAndBookstoreId(inventoryId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el libro en el inventario de la librería."));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("El precio debe ser mayor a cero.");
        }
    }

    public InventoryPricePointResponse toResponse(InventoryPrice price) {
        return new InventoryPricePointResponse(
                price.getId(),
                price.getAmount(),
                price.getEffectiveFrom(),
                price.getSource(),
                price.getPriceImport() != null ? price.getPriceImport().getId() : null,
                price.getLastConfirmedAt(),
                price.getLastConfirmedSource(),
                price.getCreatedAt()
        );
    }

    private String normalizeConfirmationSource(String source) {
        if (source == null) {
            return null;
        }

        String normalized = source.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}
