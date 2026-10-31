package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisScoredBook;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisScoredInventory;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisTextMatcher {

    private static final int RETURNED_CANDIDATE_LIMIT = 5;
    private static final int CATALOG_CANDIDATE_LIMIT = 15;
    private static final double MIN_TITLE_SCORE = 0.70;
    private static final double HIGH_CONFIDENCE_TITLE_SCORE = 0.90;
    private static final double HIGH_CONFIDENCE_SCORE = 0.80;
    private static final double HIGH_CONFIDENCE_GAP = 0.12;

    private final BookRepository bookRepository;

    public List<TiendanubeImportAnalysisScoredInventory> scoreInventories(
            String remoteName,
            List<Inventory> inventories
    ) {
        String remoteSearch = TextNormalizer.normalizeForSearch(remoteTitle(remoteName));
        String remoteMatch = TextNormalizer.normalizeForMatch(remoteAuthor(remoteName));

        if (remoteSearch == null || remoteSearch.isBlank()) {
            return List.of();
        }

        return inventories.stream()
                .map(inventory -> scoreInventory(remoteSearch, remoteMatch, inventory))
                .filter(candidate -> candidate != null)
                .sorted(Comparator.comparingDouble(TiendanubeImportAnalysisScoredInventory::score).reversed())
                .limit(RETURNED_CANDIDATE_LIMIT)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TiendanubeImportAnalysisScoredBook> scoreCatalog(String remoteName) {
        String remoteSearch = TextNormalizer.normalizeForSearch(remoteTitle(remoteName));
        String remoteMatch = TextNormalizer.normalizeForMatch(remoteAuthor(remoteName));

        if (remoteSearch == null || remoteSearch.isBlank()) {
            return List.of();
        }

        String fullTextQuery = buildCandidateFullTextQuery(remoteSearch);

        if (fullTextQuery.isBlank()) {
            return List.of();
        }

        List<Long> candidateIds = bookRepository.findTiendanubeCandidateIds(
                fullTextQuery,
                CATALOG_CANDIDATE_LIMIT
        );

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        return bookRepository.findAllWithDetailsByIdIn(candidateIds).stream()
                .map(book -> scoreBook(remoteSearch, remoteMatch, book))
                .filter(candidate -> candidate != null)
                .sorted(Comparator.comparingDouble(TiendanubeImportAnalysisScoredBook::score).reversed())
                .limit(RETURNED_CANDIDATE_LIMIT)
                .toList();
    }

    public boolean isHighConfidence(
            TiendanubeImportAnalysisScoredInventory best,
            TiendanubeImportAnalysisScoredInventory second
    ) {
        if (best.titleScore() < HIGH_CONFIDENCE_TITLE_SCORE || best.score() < HIGH_CONFIDENCE_SCORE
                || best.matchType() != TiendanubeImportAnalysisMatchType.INVENTORY_TITLE_AUTHOR) {
            return false;
        }

        return second == null || best.score() - second.score() >= HIGH_CONFIDENCE_GAP;
    }

    private TiendanubeImportAnalysisScoredInventory scoreInventory(
            String remoteSearch,
            String remoteMatch,
            Inventory inventory
    ) {
        Book book = inventory.getBook();
        double titleScore = titleScore(remoteSearch, book.getTitleSearch());
        double authorScore = titleScore >= MIN_TITLE_SCORE ? authorScore(remoteMatch, book) : 0;
        double publisherScore = 0; // La publicación remota no aporta una editorial estructurada.
        if (!isPlausible(titleScore, authorScore)) return null;
        double score = weightedScore(titleScore, authorScore, publisherScore);

        return new TiendanubeImportAnalysisScoredInventory(
                inventory,
                titleScore,
                score,
                resolveMatchType(titleScore, authorScore, publisherScore)
        );
    }

    private TiendanubeImportAnalysisScoredBook scoreBook(
            String remoteSearch,
            String remoteMatch,
            Book book
    ) {
        double titleScore = titleScore(remoteSearch, book.getTitleSearch());
        double authorScore = titleScore >= MIN_TITLE_SCORE ? authorScore(remoteMatch, book) : 0;
        double publisherScore = 0; // La publicación remota no aporta una editorial estructurada.

        if (!isPlausible(titleScore, authorScore)) return null;
        return new TiendanubeImportAnalysisScoredBook(
                book,
                titleScore,
                weightedScore(titleScore, authorScore, publisherScore)
        );
    }

    private boolean isPlausible(double title, double author) {
        // Título completo idéntico: se puede revisar incluso si el autor difiere.
        // Título parcial: exige corroboración del autor, nunca una palabra aislada.
        return title >= 0.98 || (title >= MIN_TITLE_SCORE && author >= 0.45);
    }

    private double weightedScore(double titleScore, double authorScore, double publisherScore) {
        return titleScore * 0.80 + authorScore * 0.12 + publisherScore * 0.08;
    }

    private TiendanubeImportAnalysisMatchType resolveMatchType(
            double titleScore,
            double authorScore,
            double publisherScore
    ) {
        if (titleScore >= HIGH_CONFIDENCE_TITLE_SCORE && authorScore >= 0.70) {
            return TiendanubeImportAnalysisMatchType.INVENTORY_TITLE_AUTHOR;
        }

        if (titleScore >= HIGH_CONFIDENCE_TITLE_SCORE && publisherScore >= 0.70) {
            return TiendanubeImportAnalysisMatchType.INVENTORY_TITLE_PUBLISHER;
        }

        if (titleScore >= HIGH_CONFIDENCE_TITLE_SCORE) {
            return TiendanubeImportAnalysisMatchType.INVENTORY_TITLE;
        }

        return TiendanubeImportAnalysisMatchType.FUZZY_TITLE;
    }

    private double authorScore(String remoteName, Book book) {
        if (remoteName == null || book.getAuthors() == null || book.getAuthors().isEmpty()) {
            return 0;
        }

        return book.getAuthors().stream()
                .map(Author::getName)
                .map(TextNormalizer::normalizeForMatch)
                .filter(value -> value != null && !value.isBlank())
                .mapToDouble(author -> titleScore(remoteName, author))
                .max()
                .orElse(0);
    }

    private double publisherScore(String remoteName, Book book) {
        if (remoteName == null || book.getPublisher() == null || book.getPublisher().getName() == null) {
            return 0;
        }

        String publisher = TextNormalizer.normalizeForMatch(book.getPublisher().getName());
        return publisher == null || publisher.isBlank() ? 0 : titleScore(remoteName, publisher);
    }

    // Tiendanube suele publicar "Título - Autor" en un único campo. No se debe
    // usar el apellido del autor como prueba de coincidencia con el título.
    private String remoteTitle(String name) {
        if (name == null) return null;
        int separator = name.lastIndexOf(" - ");
        return separator > 0 ? name.substring(0, separator).trim() : name;
    }

    private String remoteAuthor(String name) {
        if (name == null) return null;
        int separator = name.lastIndexOf(" - ");
        return separator > 0 ? name.substring(separator + 3).trim() : null;
    }

    private double titleScore(String remoteName, String title) {
        if (remoteName == null || remoteName.isBlank() || title == null || title.isBlank()) return 0;
        List<String> remote = significantTokens(remoteName);
        List<String> candidate = significantTokens(TextNormalizer.normalizeForSearch(title));
        if (remote.isEmpty() || candidate.isEmpty()) return 0;
        double forward = tokenCoverage(remote, candidate);
        double backward = tokenCoverage(candidate, remote);
        // Cobertura bidireccional: "Nada" no equivale a "Rezos a la Nada".
        // No se recompensa repetir una misma palabra varias veces.
        double harmonic = forward + backward == 0 ? 0 : 2 * forward * backward / (forward + backward);
        // Coincidencias parciales sin el título completo necesitan pruebas adicionales.
        return harmonic;
    }

    private double tokenCoverage(List<String> source, List<String> target) {
        double total = 0;
        for (String word : source) {
            total += target.stream().mapToDouble(other -> tokenSimilarity(word, other)).max().orElse(0);
        }
        return total / source.size();
    }

    private List<String> significantTokens(String value) {
        return Stream.of(value.split("\\s+"))
                .filter(token -> token.length() > 1)
                .filter(token -> !java.util.Set.of("de", "del", "la", "las", "los", "el", "un", "una", "unos", "unas", "en", "por", "para", "con", "sin", "y", "al", "a", "the", "and").contains(token))
                .toList();
    }

    private double tokenSimilarity(String first, String second) {
        if (first.equals(second)) {
            return 1;
        }

        int minLength = Math.min(first.length(), second.length());
        int maxLength = Math.max(first.length(), second.length());

        if (minLength >= 5 && (first.startsWith(second) || second.startsWith(first))) {
            return (double) minLength / maxLength;
        }

        return 0;
    }

    private String buildCandidateFullTextQuery(String normalizedName) {
        return Stream.of(normalizedName.split("\\s+"))
                .filter(token -> token.length() > 2)
                .map(token -> token + ":*")
                .collect(Collectors.joining(" | "));
    }
}
