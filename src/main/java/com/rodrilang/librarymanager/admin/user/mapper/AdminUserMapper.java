package com.rodrilang.librarymanager.admin.user.mapper;

import com.rodrilang.librarymanager.admin.bookstore.mapper.AdminBookstoreMapper;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import com.rodrilang.librarymanager.auth.mappers.RoleMapper;
import com.rodrilang.librarymanager.auth.models.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {RoleMapper.class, AdminBookstoreMapper.class})
public interface AdminUserMapper {

    AdminUserResponse toDto(User user);
}