package com.rodrilang.librarymanager.admin.bookstore.service;

import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminCreateBookstoreRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminInviteUserRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminUpdateBookstoreRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.response.AdminBookstoreResponse;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminBookstoreService {

    Page<AdminBookstoreResponse> findAll(
            String search,
            Boolean active,
            Pageable pageable
    );

    AdminBookstoreResponse findById(Long bookstoreId);

    AdminBookstoreResponse create(AdminCreateBookstoreRequest request);

    AdminBookstoreResponse update(Long bookstoreId, AdminUpdateBookstoreRequest request);

    void activate(Long bookstoreId);

    void deactivate(Long bookstoreId);

    Page<AdminUserResponse> findUsers(
            Long bookstoreId,
            String search,
            Boolean enabled,
            Boolean locked,
            Pageable pageable
    );

    void inviteUser(Long bookstoreId, AdminInviteUserRequest request);
}