package com.rodrilang.librarymanager.integrations.tiendanube.event;

public record BookPublicationChangedEvent(Long bookId, Long bookstoreId) {
    public BookPublicationChangedEvent(Long bookId) {
        this(bookId, null);
    }
}
