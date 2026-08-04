package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.request.LoginRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.LogoutRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.RefreshTokenRequest;
import com.rodrilang.librarymanager.auth.dtos.request.UserRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;

public interface AuthService {

    UserResponse register(UserRequestDto request);

    AuthResponse login(LoginRequestDto request);

    void logout(LogoutRequestDto requestDto);

    AuthResponse refresh(RefreshTokenRequest request);
}