package com.rodrilang.librarymanager.auth.exceptions;

public class PasswordReuseException extends RuntimeException {
    public PasswordReuseException(String message) {
        super(message);
    }
}
