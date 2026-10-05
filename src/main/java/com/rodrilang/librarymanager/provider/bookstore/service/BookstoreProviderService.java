package com.rodrilang.librarymanager.provider.bookstore.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.bookstore.dto.*;
import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookstoreProviderService {
    private final BookstoreContext bookstoreContext;
    private final BookstoreProviderRepository repository;
    private final ProviderRepository providerRepository;
    private final BookstoreRepository bookstoreRepository;

    @Transactional(readOnly=true)
    public List<BookstoreProviderResponse> findMine(){
        return repository.findAllByBookstoreIdAndActiveTrueOrderByProviderNameAsc(bookstoreContext.getCurrentBookstoreId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookstoreProviderResponse update(Long providerId,UpdateBookstoreProviderRequest request){
        Long bookstoreId=bookstoreContext.getCurrentBookstoreId();
        Provider provider=providerRepository.findById(providerId).orElseThrow(()->new BusinessException("No se encontró el proveedor."));
        if(provider.getType()!=ProviderType.COMMERCIAL) throw new BusinessException("El proveedor seleccionado no es comercial.");
        BookstoreProvider relation=repository.findByBookstoreIdAndProviderId(bookstoreId,providerId).orElseGet(()->{
            Bookstore bookstore=bookstoreRepository.findById(bookstoreId).orElseThrow(()->new BusinessException("No se encontró la librería."));
            return BookstoreProvider.builder().bookstore(bookstore).provider(provider).active(true).build();
        });
        if(request.active()!=null) relation.setActive(request.active());
        if(request.preferred()!=null) relation.setPreferred(request.preferred());
        relation.setNotes(request.notes()==null||request.notes().isBlank()?null:request.notes().trim());
        return toResponse(repository.save(relation));
    }

    private BookstoreProviderResponse toResponse(BookstoreProvider bp){ Provider p=bp.getProvider(); return new BookstoreProviderResponse(p.getId(),p.getCode(),p.getName(),bp.isActive(),bp.isPreferred(),bp.getNotes()); }
}
