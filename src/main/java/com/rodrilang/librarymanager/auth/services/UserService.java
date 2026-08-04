package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.request.UserRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;


public interface UserService {

    UserResponse createUser(UserRequestDto request);

    UserResponse findByUsername(String username);
}
