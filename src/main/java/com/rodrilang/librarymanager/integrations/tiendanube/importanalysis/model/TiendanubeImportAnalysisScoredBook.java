package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.model.Book;

public record TiendanubeImportAnalysisScoredBook(
        Book book,
        double titleScore,
        double score
) {
}
