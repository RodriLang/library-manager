package com.rodrilang.librarymanager.auth.exceptions;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}
