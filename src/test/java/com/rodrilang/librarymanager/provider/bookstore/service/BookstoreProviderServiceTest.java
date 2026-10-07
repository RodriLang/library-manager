package com.rodrilang.librarymanager.provider.bookstore.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.pricing.repository.BookstorePriceListFormatRepository;
import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderSource;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.provider.service.ProviderAccessService;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookstoreProviderServiceTest {

    @Test
    void withdrawsOwnPendingProviderOnlyAfterItWasRemovedFromMine() {
        BookstoreContext context = mock(BookstoreContext.class);
        BookstoreProviderRepository relations = mock(BookstoreProviderRepository.class);
        ProviderRepository providers = mock(ProviderRepository.class);
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        ProviderAccessService access = mock(ProviderAccessService.class);
        BookstorePriceListFormatRepository formats = mock(BookstorePriceListFormatRepository.class);

        BookstoreProviderService service = new BookstoreProviderService(
                context, relations, providers, bookstores, access, formats
        );

        Provider provider = Provider.builder()
                .id(20L)
                .verificationStatus(ProviderVerificationStatus.PENDING_REVIEW)
                .source(ProviderSource.BOOKSTORE)
                .createdByBookstoreId(10L)
                .active(true)
                .build();
        BookstoreProvider relation = BookstoreProvider.builder()
                .provider(provider)
                .active(false)
                .preferred(true)
                .build();

        when(context.getCurrentBookstoreId()).thenReturn(10L);
        when(providers.findById(20L)).thenReturn(Optional.of(provider));
        when(relations.findByBookstoreIdAndProviderId(10L, 20L)).thenReturn(Optional.of(relation));

        service.withdraw(20L);

        assertEquals(ProviderVerificationStatus.WITHDRAWN, provider.getVerificationStatus());
        assertFalse(provider.isActive());
        assertFalse(relation.isPreferred());
        verify(providers).save(provider);
        verify(relations).save(relation);
        verify(formats).deactivateAllByBookstoreIdAndProviderId(10L, 20L);
    }

    @Test
    void doesNotWithdrawWhileProviderIsStillInMine() {
        BookstoreContext context = mock(BookstoreContext.class);
        BookstoreProviderRepository relations = mock(BookstoreProviderRepository.class);
        ProviderRepository providers = mock(ProviderRepository.class);
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        ProviderAccessService access = mock(ProviderAccessService.class);
        BookstorePriceListFormatRepository formats = mock(BookstorePriceListFormatRepository.class);

        BookstoreProviderService service = new BookstoreProviderService(
                context, relations, providers, bookstores, access, formats
        );

        Provider provider = Provider.builder()
                .id(20L)
                .verificationStatus(ProviderVerificationStatus.PENDING_REVIEW)
                .source(ProviderSource.BOOKSTORE)
                .createdByBookstoreId(10L)
                .active(true)
                .build();
        BookstoreProvider relation = BookstoreProvider.builder()
                .provider(provider)
                .active(true)
                .build();

        when(context.getCurrentBookstoreId()).thenReturn(10L);
        when(providers.findById(20L)).thenReturn(Optional.of(provider));
        when(relations.findByBookstoreIdAndProviderId(10L, 20L)).thenReturn(Optional.of(relation));

        assertThrows(BusinessException.class, () -> service.withdraw(20L));

        verify(providers, never()).save(provider);
        verify(formats, never()).deactivateAllByBookstoreIdAndProviderId(10L, 20L);
    }
}
