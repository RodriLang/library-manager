package com.rodrilang.librarymanager.store.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.dto.SalesChannelSettingsResponse;
import com.rodrilang.librarymanager.store.model.BookstoreSalesChannel;
import com.rodrilang.librarymanager.store.repository.BookstoreSalesChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class SalesChannelService {
    private final BookstoreContext bookstoreContext;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreSalesChannelRepository repository;

    @Transactional(readOnly = true)
    public SalesChannelSettingsResponse current() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return new SalesChannelSettingsResponse(isEnabled(bookstoreId, SalesChannelType.ANAQUEL_STORE), isEnabled(bookstoreId, SalesChannelType.TIENDANUBE));
    }

    @Transactional
    public SalesChannelSettingsResponse setEnabled(SalesChannelType channel, boolean enabled) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreSalesChannel row = repository.findByBookstoreIdAndChannel(bookstoreId, channel).orElseGet(() -> {
            Bookstore bookstore = bookstoreRepository.findById(bookstoreId).orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería."));
            return BookstoreSalesChannel.builder().bookstore(bookstore).channel(channel).enabled(false).build();
        });
        row.setEnabled(enabled);
        repository.save(row);
        return new SalesChannelSettingsResponse(isEnabled(bookstoreId, SalesChannelType.ANAQUEL_STORE), isEnabled(bookstoreId, SalesChannelType.TIENDANUBE));
    }

    public boolean isEnabled(Long bookstoreId, SalesChannelType channel) {
        return repository.findByBookstoreIdAndChannel(bookstoreId, channel).map(BookstoreSalesChannel::getEnabled).orElse(false);
    }
}
