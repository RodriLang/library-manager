package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.request.RoleRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.RoleResponse;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.models.Role;

import java.util.List;

public interface RoleService {

    Role findByName(RoleType roleName);

    List<RoleResponse> findAll();

    RoleResponse findById(Long id);

    RoleResponse create(RoleRequestDto request);

}