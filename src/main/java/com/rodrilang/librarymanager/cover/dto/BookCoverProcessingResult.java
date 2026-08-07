package com.rodrilang.librarymanager.cover.dto;

public record BookCoverProcessingResult(
        int claimed,
        int completed,
        int failed
) {
}