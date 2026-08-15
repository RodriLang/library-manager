package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.request.LoginRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.LogoutRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.RefreshTokenRequest;
import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;

public interface AuthService {

    AuthResponse login(LoginRequestDto request);

    void logout(LogoutRequestDto requestDto);

    AuthResponse refresh(RefreshTokenRequest request);
}