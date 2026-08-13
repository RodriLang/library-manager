package com.rodrilang.librarymanager.auth.services;

public interface PasswordResetService {

    void requestReset(String email);

    void resetPassword(String token, String newPassword);
}