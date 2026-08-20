package com.rodrilang.librarymanager.auth.services;

public interface SecureTokenService {

    String generate(int bytes);

    String hash(String rawToken);
}