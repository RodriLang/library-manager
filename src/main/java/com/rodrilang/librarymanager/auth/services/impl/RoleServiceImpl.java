package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.dtos.request.RoleRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.RoleResponse;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.mappers.RoleMapper;
import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.repositories.RoleRepository;
import com.rodrilang.librarymanager.auth.services.RoleService;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional(readOnly = true)
    public Role findByName(RoleType roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el rol " + roleName + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAll()
                .stream()
                .map(roleMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse findById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el rol solicitado."));

        return roleMapper.toDto(role);
    }

    @Override
    @Transactional
    public RoleResponse create(RoleRequestDto request) {
        if (roleRepository.existsByRoleName(request.name())) {
            throw new DuplicateResourceException("El rol " + request.name() + " ya existe.");
        }

        Role role = Role.builder()
                .roleName(request.name())
                .build();

        Role savedRole = roleRepository.save(role);

        return roleMapper.toDto(savedRole);
    }
}