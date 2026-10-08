package com.rodrilang.librarymanager.store.dto;
import com.rodrilang.librarymanager.store.model.StoreDomainStatus;
import com.rodrilang.librarymanager.store.model.StoreDomainType;
public record StoreDomainResponse(Long id, String hostname, StoreDomainType type, StoreDomainStatus status, String verificationToken) {}
