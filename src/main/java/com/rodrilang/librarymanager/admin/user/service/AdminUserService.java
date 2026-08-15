package com.rodrilang.librarymanager.admin.user.service;

import com.rodrilang.librarymanager.admin.user.dto.AdminUpdateUserRequest;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    Page<AdminUserResponse> findAll(
            String search,
            Boolean enabled,
            Boolean locked,
            Pageable pageable
    );

    AdminUserResponse findById(Long userId);

    AdminUserResponse updateUser(Long userId, AdminUpdateUserRequest request);

    void lock(Long userId);

    void unlock(Long userId);

    void disable(Long userId);

    void enable(Long userId);

    void revokeSessions(Long userId);

    void sendPasswordReset(Long userId);
}