package com.rodrilang.librarymanager.purchasing.requirement.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderItemRepository;
import com.rodrilang.librarymanager.purchasing.preference.service.ProviderPreferenceService;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import com.rodrilang.librarymanager.purchasing.requirement.mapper.PurchaseRequirementMapper;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSource;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementRepository;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementSourceRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.BookstoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRequirementServiceImplTest {

    @Mock
    private PurchaseRequirementRepository requirementRepository;
    @Mock
    private PurchaseRequirementSourceRepository sourceRepository;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock
    private ProviderBookRepository providerBookRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private BookstoreProviderBookTermRepository providerBookTermRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private PurchaseRequirementMapper purchaseRequirementMapper;
    @Mock
    private ProviderPreferenceService providerPreferenceService;
    @Mock
    private BookService bookService;
    @Mock
    private BookstoreService bookstoreService;
    @Mock
    private BookstoreContext bookstoreContext;

    @InjectMocks
    private PurchaseRequirementServiceImpl service;

    @Test
    void increasingFullyCoveredDraftRequirementAlsoIncreasesDraftOrderItem() {
        PurchaseRequirement requirement = requirement(10L, 2);
        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .id(20L)
                .requirement(requirement)
                .quantity(2)
                .requirementQuantity(2)
                .build();

        mockPendingRequirement(requirement);
        when(purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(10L)).thenReturn(2L);
        when(purchaseOrderItemRepository.sumLockedOrderedQuantityByRequirementId(10L)).thenReturn(0L);
        when(purchaseOrderItemRepository.findDraftItemsByRequirementIdForUpdate(10L))
                .thenReturn(List.of(item));
        when(sourceRepository.save(any(PurchaseRequirementSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adjust(10L, 3);

        assertEquals(3, requirement.getQuantity());
        assertEquals(3, item.getQuantity());
        assertEquals(3, item.getRequirementQuantity());
    }

    @Test
    void increasingRequirementCoveredOnlyBySentOrderLeavesNewUnitToReplenish() {
        PurchaseRequirement requirement = requirement(10L, 2);

        mockPendingRequirement(requirement);
        when(purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(10L)).thenReturn(2L);
        when(purchaseOrderItemRepository.sumLockedOrderedQuantityByRequirementId(10L)).thenReturn(2L);
        when(purchaseOrderItemRepository.findDraftItemsByRequirementIdForUpdate(10L))
                .thenReturn(List.of());
        when(sourceRepository.save(any(PurchaseRequirementSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adjust(10L, 3);

        assertEquals(3, requirement.getQuantity());
        verify(purchaseOrderItemRepository).findDraftItemsByRequirementIdForUpdate(10L);
    }

    @Test
    void cannotReduceRequirementBelowQuantityAlreadySent() {
        PurchaseRequirement requirement = requirement(10L, 3);

        mockPendingRequirement(requirement);
        when(purchaseOrderItemRepository.sumOrderedQuantityByRequirementId(10L)).thenReturn(3L);
        when(purchaseOrderItemRepository.sumLockedOrderedQuantityByRequirementId(10L)).thenReturn(2L);

        assertThrows(BusinessException.class, () -> service.adjust(10L, 1));

        verify(sourceRepository, never()).save(any(PurchaseRequirementSource.class));
        assertEquals(3, requirement.getQuantity());
    }

    private void mockPendingRequirement(PurchaseRequirement requirement) {
        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(1L);
        when(requirementRepository.findByIdAndBookstoreIdAndStatusForUpdate(
                requirement.getId(),
                1L,
                PurchaseRequirementStatus.PENDING
        )).thenReturn(Optional.of(requirement));
    }

    private PurchaseRequirement requirement(Long id, int quantity) {
        return PurchaseRequirement.builder()
                .id(id)
                .quantity(quantity)
                .status(PurchaseRequirementStatus.PENDING)
                .build();
    }
}
