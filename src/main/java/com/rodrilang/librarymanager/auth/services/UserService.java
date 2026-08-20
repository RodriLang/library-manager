package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.request.UpdateProfileRequest;
import com.rodrilang.librarymanager.auth.dtos.request.UserRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.invitation.dto.InvitationRegisterRequest;
import com.rodrilang.librarymanager.model.Bookstore;


public interface UserService {

    UserResponse createUser(UserRequestDto request);

    UserResponse createUserFromInvitation(
            InvitationRegisterRequest request,
            Bookstore bookstore,
            RoleType roleType
    );

    UserResponse findById(Long userId);

    UserResponse findByUsername(String identifier);

    UserResponse updateProfile(
            Long userId,
            UpdateProfileRequest request
    );
}
