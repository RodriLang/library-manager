package com.rodrilang.librarymanager.bookstore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BookstoreContext {

    @Value("${app.default-bookstore-id}")
    private Long defaultBookstoreId;

    public Long getCurrentBookstoreId() {
        return defaultBookstoreId;
    }
}