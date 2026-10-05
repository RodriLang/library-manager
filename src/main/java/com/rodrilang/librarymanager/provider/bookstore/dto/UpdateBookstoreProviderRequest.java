package com.rodrilang.librarymanager.provider.bookstore.dto;

import jakarta.validation.constraints.Size;

public record UpdateBookstoreProviderRequest(Boolean active,Boolean preferred,@Size(max=2000) String notes) {}
