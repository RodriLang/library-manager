package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeAuthorizationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeTokenResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOAuthService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOAuthStateService;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeOAuthServiceImpl implements TiendanubeOAuthService {

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProperties properties;
    private final TiendanubeClient tiendanubeClient;
    private final TiendanubeOAuthStateService stateService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    @Override
    public TiendanubeAuthorizationResponse createAuthorizationUrl() {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        String state = stateService.create(bookstoreId);

        String authorizationUrl = UriComponentsBuilder
                .fromUriString(properties.authUrl())
                .pathSegment(properties.clientId(), "authorize")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();

        return new TiendanubeAuthorizationResponse(authorizationUrl);
    }

    @Override
    @Transactional
    public void handleCallback(String code, String state) {
        validateCode(code);

        Long bookstoreId = stateService.validateAndConsume(state);

        String response = tiendanubeClient.exchangeCodeForToken(code);

        log.info("Tiendanube token response: {}", response);

        throw new RuntimeException("Ver logs");
    }

    /*   @Override
       @Transactional
       public void handleCallback(String code, String state) {
           validateCode(code);

           Long bookstoreId = stateService.validateAndConsume(state);

           TiendanubeTokenResponse response = tiendanubeClient.exchangeCodeForToken(code);

           if (response == null) {
               throw new IllegalStateException("No se pudo obtener el token de Tiendanube.");
           }

           Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

           TiendanubeStore store = storeRepository.findByBookstoreId(bookstoreId).orElseGet(TiendanubeStore::new);

           store.setBookstore(bookstore);
           store.setStoreId(response.userId());
           store.setAccessToken(response.accessToken());
           store.setTokenType(response.tokenType());
           store.setScope(response.scope());
           store.setActive(true);
           store.setConnectedAt(Instant.now());

           storeRepository.save(store);
       }
   */

    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización es obligatorio.");
        }
    }
}