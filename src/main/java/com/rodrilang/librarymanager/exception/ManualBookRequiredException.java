package com.rodrilang.librarymanager.exception;

import lombok.Getter;

@Getter
public class ManualBookRequiredException extends RuntimeException {

    private final String isbn;

    public ManualBookRequiredException(String isbn) {
        super("No se encontró información del libro en las fuentes disponibles. Complete los datos manualmente para continuar.");
        this.isbn = isbn;
    }
}