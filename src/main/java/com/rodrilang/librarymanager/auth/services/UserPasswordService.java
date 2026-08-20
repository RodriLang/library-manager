package com.rodrilang.librarymanager.auth.services;

public interface UserPasswordService {

    void changePassword(
            Long userId,
            String currentPassword,
            String newPassword
    );
}