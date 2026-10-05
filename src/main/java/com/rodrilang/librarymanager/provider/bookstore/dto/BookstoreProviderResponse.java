package com.rodrilang.librarymanager.provider.bookstore.dto;

public record BookstoreProviderResponse(Long providerId,String code,String name,boolean active,boolean preferred,String notes) {}
