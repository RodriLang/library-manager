package com.rodrilang.librarymanager.admin.dashboard.dto;

public record AdminDashboardResponse(
        long books,
        long bookstores,
        long users,
        long providers,
        long booksWithoutCover,
        long booksWithoutAuthor,
        long booksWithoutPublisher,
        long booksWithoutIsbn,
        long booksIncomplete
) {}
