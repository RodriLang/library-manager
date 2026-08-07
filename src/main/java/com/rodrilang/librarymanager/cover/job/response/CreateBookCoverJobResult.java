package com.rodrilang.librarymanager.cover.job.response;

public record CreateBookCoverJobResult(
        Long jobId,
        boolean created,
        String reason
) {

    public static CreateBookCoverJobResult created(Long jobId) {
        return new CreateBookCoverJobResult(
                jobId,
                true,
                null
        );
    }

    public static CreateBookCoverJobResult duplicate(Long jobId) {
        return new CreateBookCoverJobResult(
                jobId,
                false,
                "DUPLICATE"
        );
    }
}