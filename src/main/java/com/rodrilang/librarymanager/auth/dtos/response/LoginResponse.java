package com.rodrilang.librarymanager.auth.dtos.response;

import lombok.Builder;

import java.util.List;

@Builder
public record LoginResponse(
        String username,
        List<String> roles,
        String message
) {
}
