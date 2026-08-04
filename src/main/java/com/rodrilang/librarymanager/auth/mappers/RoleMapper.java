package com.rodrilang.librarymanager.auth.mappers;

import com.rodrilang.librarymanager.auth.dtos.response.RoleResponse;
import com.rodrilang.librarymanager.auth.models.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(source = "roleName", target = "role")
    RoleResponse toDto(Role entity);
}
