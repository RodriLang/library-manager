package com.rodrilang.librarymanager.store.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.store.dto.*;
import com.rodrilang.librarymanager.store.model.*;
import com.rodrilang.librarymanager.store.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreSectionService {
    private final BookstoreContext bookstoreContext;
    private final BookstoreStoreRepository storeRepository;
    private final StoreSectionRepository sectionRepository;
    private final StoreSectionItemRepository itemRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public List<StoreSectionResponse> currentSections() {
        BookstoreStore store = requireCurrentStore();
        ensureSystemSections(store);
        return sectionRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(store.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public StoreSectionResponse createCustom(CreateStoreSectionRequest request) {
        BookstoreStore store = requireCurrentStore();
        ensureSystemSections(store);
        String name = request.name().trim();
        String slug = uniqueSlug(store.getId(), slugify(name));
        int nextOrder = sectionRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(store.getId()).stream()
                .map(StoreSection::getDisplayOrder).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 10;
        StoreSection section = sectionRepository.save(StoreSection.builder()
                .store(store).type(StoreSectionType.CUSTOM).name(name).slug(slug).enabled(true)
                .displayOrder(nextOrder).windowDays(null).maxItems(8).build());
        return toResponse(section);
    }

    @Transactional
    public StoreSectionResponse update(Long sectionId, UpdateStoreSectionRequest request) {
        StoreSection section = requireOwnedSection(sectionId);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isBlank()) throw new BusinessException("El nombre de la sección no puede estar vacío.");
            section.setName(name);
            if (section.getType() == StoreSectionType.CUSTOM) section.setSlug(uniqueSlugExcluding(section.getStore().getId(), slugify(name), section.getId()));
        }
        if (request.enabled() != null) section.setEnabled(request.enabled());
        if (request.displayOrder() != null) section.setDisplayOrder(request.displayOrder());
        if (request.maxItems() != null) section.setMaxItems(request.maxItems());
        if (request.windowDays() != null) {
            if (section.getType() != StoreSectionType.NEW_ARRIVALS && section.getType() != StoreSectionType.RESTOCKED) {
                throw new BusinessException("Esta sección no utiliza una ventana de días.");
            }
            section.setWindowDays(request.windowDays());
        }
        return toResponse(sectionRepository.save(section));
    }

    @Transactional
    public List<StoreSectionResponse> reorder(UpdateStoreSectionsOrderRequest request) {
        BookstoreStore store = requireCurrentStore();
        ensureSystemSections(store);
        List<StoreSection> sections = sectionRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(store.getId());
        Map<Long, StoreSection> byId = new HashMap<>();
        sections.forEach(section -> byId.put(section.getId(), section));
        if (request.sectionIds().size() != sections.size() || !byId.keySet().equals(new HashSet<>(request.sectionIds()))) {
            throw new BusinessException("El orden debe incluir todas las secciones de la tienda.");
        }
        int order = 10;
        for (Long id : request.sectionIds()) {
            byId.get(id).setDisplayOrder(order);
            order += 10;
        }
        sectionRepository.saveAll(sections);
        return sectionRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(store.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long sectionId) {
        StoreSection section = requireOwnedSection(sectionId);
        if (section.getType() != StoreSectionType.CUSTOM) throw new BusinessException("Las secciones del sistema no se pueden eliminar; podés desactivarlas.");
        sectionRepository.delete(section);
    }

    @Transactional(readOnly = true)
    public List<Long> itemIds(Long sectionId) {
        StoreSection section = requireOwnedSection(sectionId);
        if (section.getType() != StoreSectionType.CUSTOM) return List.of();
        return itemRepository.findInventoryIdsBySectionId(sectionId);
    }

    @Transactional
    public StoreSectionResponse replaceItems(Long sectionId, UpdateStoreSectionItemsRequest request) {
        StoreSection section = requireOwnedSection(sectionId);
        if (section.getType() != StoreSectionType.CUSTOM) throw new BusinessException("Sólo las secciones personalizadas admiten una selección manual de libros.");
        List<Long> ids = request.inventoryIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > 200) throw new BusinessException("Una sección personalizada puede contener hasta 200 libros.");
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        List<Inventory> inventories = ids.isEmpty() ? List.of() : inventoryRepository.findAllById(ids).stream()
                .filter(i -> i.getBookstore() != null && Objects.equals(i.getBookstore().getId(), bookstoreId)).toList();
        if (inventories.size() != ids.size()) throw new BusinessException("Hay libros que no pertenecen al inventario de esta librería.");
        Map<Long, Inventory> byId = new HashMap<>();
        inventories.forEach(i -> byId.put(i.getId(), i));
        itemRepository.deleteAllBySectionId(sectionId);
        itemRepository.flush();
        List<StoreSectionItem> items = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            items.add(StoreSectionItem.builder().section(section).inventory(byId.get(ids.get(index))).displayOrder((index + 1) * 10).build());
        }
        itemRepository.saveAll(items);
        return toResponse(section);
    }

    @Transactional
    public void ensureSystemSections(BookstoreStore store) {
        ensureSystem(store, StoreSectionType.FEATURED, "Destacados", "destacados", 10, null, true);
        ensureSystem(store, StoreSectionType.NEW_ARRIVALS, "Novedades", "novedades", 20, 45, true);
        ensureSystem(store, StoreSectionType.RESTOCKED, "Reingresos", "reingresos", 30, 30, false);
    }

    @Transactional(readOnly = true)
    public Optional<StoreSection> findSystem(Long storeId, StoreSectionType type) {
        return sectionRepository.findByStoreIdAndType(storeId, type);
    }

    private void ensureSystem(BookstoreStore store, StoreSectionType type, String name, String slug, int order, Integer windowDays, boolean enabled) {
        if (sectionRepository.findByStoreIdAndType(store.getId(), type).isPresent()) return;
        sectionRepository.save(StoreSection.builder().store(store).type(type).name(name).slug(slug).enabled(enabled)
                .displayOrder(order).windowDays(windowDays).maxItems(8).build());
    }

    private StoreSection requireOwnedSection(Long id) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return sectionRepository.findById(id)
                .filter(section -> Objects.equals(section.getStore().getBookstore().getId(), bookstoreId))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sección."));
    }

    private BookstoreStore requireCurrentStore() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return storeRepository.findByBookstoreId(bookstoreId).orElseThrow(() -> new ResourceNotFoundException("Primero configurá la tienda online."));
    }

    private StoreSectionResponse toResponse(StoreSection section) {
        return new StoreSectionResponse(section.getId(), section.getType(), section.getName(), section.getSlug(), Boolean.TRUE.equals(section.getEnabled()),
                section.getDisplayOrder(), section.getWindowDays(), section.getMaxItems(), itemRepository.countBySectionId(section.getId()));
    }

    private String uniqueSlug(Long storeId, String base) {
        String safe = base.isBlank() ? "seccion" : base;
        String candidate = safe;
        int suffix = 2;
        while (sectionRepository.existsByStoreIdAndSlugIgnoreCase(storeId, candidate)) candidate = safe + "-" + suffix++;
        return candidate;
    }

    private String uniqueSlugExcluding(Long storeId, String base, Long sectionId) {
        String safe = base.isBlank() ? "seccion" : base;
        String candidate = safe;
        int suffix = 2;
        while (true) {
            Optional<StoreSection> existing = sectionRepository.findByStoreIdAndSlugIgnoreCase(storeId, candidate);
            if (existing.isEmpty() || Objects.equals(existing.get().getId(), sectionId)) return candidate;
            candidate = safe + "-" + suffix++;
        }
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
