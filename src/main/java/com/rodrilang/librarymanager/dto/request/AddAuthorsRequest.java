package com.rodrilang.librarymanager.dto.request;

import java.util.Set;

public record AddAuthorsRequest(

        Set<Long> authorIds
) {
}