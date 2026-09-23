package com.rodrilang.librarymanager.auth.access.mapper;

import com.rodrilang.librarymanager.auth.access.dto.BookstoreMembershipResponse;
import com.rodrilang.librarymanager.auth.access.model.BookstoreMembership;
import com.rodrilang.librarymanager.auth.mappers.RoleMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = RoleMapper.class)
public interface BookstoreMembershipMapper {
    @Mapping(source = "bookstore.id", target = "bookstoreId")
    @Mapping(source = "bookstore.name", target = "bookstoreName")
    BookstoreMembershipResponse toDto(BookstoreMembership membership);
}
