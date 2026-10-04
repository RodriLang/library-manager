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
    private static final String MANUAL_PRICE_SOURCE = "Carga manual";
    private static final String MANUAL_CONFIRMATION_SOURCE = "Confirmación manual";
    private static final String PRICE_LIST_CONFIRMATION_SOURCE = "Lista de precios";

    private final InventoryPriceRepository priceRepository;
    private final InventoryRepository inventoryRepository;
    private final BookstoreContext bookstoreContext;
    private final ApplicationEventPublisher eventPublisher;

    public LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * The list retains its declared month in InventoryPriceImport. Prices from
     * lists already in force take effect when applied, without backdating the
     * change or being hidden behind a later manual or migrated price.
     */
    public LocalDate importApplicationDate(LocalDate effectiveFrom) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        LocalDate currentDate = today();
        return effectiveFrom.isAfter(currentDate) ? effectiveFrom : currentDate;
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
        markConfirmed(price, today(), MANUAL_PRICE_SOURCE, true);
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
        validateAmount(amount);
        LocalDate applicationDate = importApplicationDate(effectiveFrom);
        LocalDate confirmationDate = today();
        String confirmationSource = priceImport != null
                ? normalizeConfirmationSource(priceImport.getSourceName())
                : null;
        if (confirmationSource == null) {
            confirmationSource = PRICE_LIST_CONFIRMATION_SOURCE;
        }
        InventoryPrice applicablePrice = priceAt(inventory.getId(), applicationDate).orElse(null);

        // Re-read at application time: the inventory price or the business day
        // may have changed since the preview was created.
        if (applicablePrice != null && applicablePrice.getAmount().compareTo(amount) == 0) {
            markConfirmed(applicablePrice, confirmationDate, confirmationSource, true);
            return applicablePrice;
        }

        InventoryPrice price = upsert(
                inventory,
                amount,
                applicationDate,
                InventoryPriceSource.PRICE_LIST,
                priceImport,
                userId,
                true
        );

        // Confirmation date answers "when did we verify this amount?" and is
        // intentionally independent from effectiveFrom, including future lists.
        markConfirmed(price, confirmationDate, confirmationSource, true);
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
        InventoryPrice price = upsert(inventory, amount, effectiveFrom, source, null, userId, true);
        markConfirmed(price, today(), confirmationSourceFor(source), true);
        return price;
    }

    /**
     * Confirms the price that was in force on the supplied date. Kept for
     * internal use; manual UI confirmation should use confirmCurrentPrice.
     */
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

        markConfirmed(price, confirmedAt, confirmedSource, true);
    }

    /**
     * Manually confirms the current price without changing its amount or
     * effectiveFrom and without creating a new history row.
     */
    @Transactional
    public InventoryPricePointResponse confirmCurrentPrice(
            Long inventoryId,
            LocalDate confirmedAt,
            String confirmedSource
    ) {
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        LocalDate currentDate = today();
        if (confirmedAt.isAfter(currentDate)) {
            throw new BusinessException("La fecha de confirmación no puede ser futura.");
        }

        Inventory inventory = getCurrentBookstoreInventory(inventoryId);
        InventoryPrice currentPrice = priceRepository.findCurrentCandidates(inventory.getId(), currentDate)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("El libro no tiene un precio vigente para confirmar."));

        if (confirmedAt.isBefore(currentPrice.getEffectiveFrom())) {
            throw new BusinessException("La fecha de confirmación no puede ser anterior al inicio de vigencia del precio actual.");
        }

        String source = normalizeConfirmationSource(confirmedSource);
        markConfirmed(
                currentPrice,
                confirmedAt,
                source != null ? source : MANUAL_CONFIRMATION_SOURCE,
                false
        );

        return toResponse(currentPrice);
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
     * Prices are now resolved directly from inventory_prices. This scheduler only
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

    private void markConfirmed(
            InventoryPrice price,
            LocalDate confirmedAt,
            String confirmedSource,
            boolean preserveLatestConfirmation
    ) {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(confirmedAt, "confirmedAt");

        if (preserveLatestConfirmation
                && price.getLastConfirmedAt() != null
                && confirmedAt.isBefore(price.getLastConfirmedAt())) {
            return;
        }

        price.setLastConfirmedAt(confirmedAt);
        price.setLastConfirmedSource(normalizeConfirmationSource(confirmedSource));
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

    private String confirmationSourceFor(InventoryPriceSource source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case MANUAL -> MANUAL_PRICE_SOURCE;
            case PRICE_LIST -> PRICE_LIST_CONFIRMATION_SOURCE;
            case STOCK_LOAD -> "Carga de stock";
            case TIENDANUBE_IMPORT -> "Importación de Tiendanube";
            case PURCHASE -> "Compra";
            case SALE_RESOLUTION -> "Resolución de venta";
            case LEGACY_MIGRATION -> "Migración anterior";
        };
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
