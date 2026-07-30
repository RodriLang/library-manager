package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;

import java.util.List;

public interface TiendanubeProductMatchingService {

    TiendanubeRemoteProductResponse analyze(
            Long bookstoreId,
            Long storeId,
            TiendanubeProductResponse product
    );

    RemoteInventoryMatch findRemoteMatch(
            Inventory inventory,
            List<TiendanubeProductResponse> products
    );

    List<Book> findBookCandidates(TiendanubeProductResponse product);
}