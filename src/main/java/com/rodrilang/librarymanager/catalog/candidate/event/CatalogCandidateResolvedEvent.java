package com.rodrilang.librarymanager.catalog.candidate.event;

public record CatalogCandidateResolvedEvent(

        Long candidateId,

        Long bookId

) {
}
