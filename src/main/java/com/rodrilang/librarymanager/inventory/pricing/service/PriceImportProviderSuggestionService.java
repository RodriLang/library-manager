package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.pricing.model.BookstorePriceListFormat;
import com.rodrilang.librarymanager.inventory.pricing.repository.BookstorePriceListFormatRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PriceImportProviderSuggestionService {
    private final BookstoreContext bookstoreContext;
    private final BookstorePriceListFormatRepository formats;
    private final InventoryPriceImportRepository imports;

    public record Suggestion(Long providerId, String providerName, int score, List<String> reasons) {}
    public record Suggestions(List<Suggestion> candidates, boolean ambiguous) {}

    @Transactional(readOnly = true)
    public Suggestions suggest(Long formatId, String filename) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        var savedFormats = formats.findAllByBookstoreIdAndActiveTrueOrderByStandardDescNameAsc(bookstoreId);
        BookstorePriceListFormat selected = savedFormats.stream()
                .filter(f -> Objects.equals(f.getId(), formatId)).findFirst().orElse(null);
        Map<Long, Score> scores = new HashMap<>();

        // A saved format is an indication, not proof: distributors may share layouts.
        if (selected != null && selected.getProvider() != null) {
            add(scores, selected.getProvider(), 40, "Proveedor asociado al formato");
        }
        var recent = imports.findTop100ByBookstoreIdAndProviderIsNotNullOrderByCreatedAtDesc(bookstoreId);
        Map<Long, Integer> occurrences = new HashMap<>();
        for (var entry : recent) {
            Provider provider = entry.getProvider();
            if (provider == null) continue;
            if (selected != null && entry.getFormat() != null && Objects.equals(entry.getFormat().getId(), selected.getId())) {
                add(scores, provider, 14, "Importaciones anteriores con este formato");
            }
            occurrences.merge(provider.getId(), 1, Integer::sum);
        }
        // Filename is useful only if a complete provider name/code is present.
        String normalizedFilename = normalize(filename);
        Map<Long, Provider> known = new HashMap<>();
        savedFormats.stream().map(BookstorePriceListFormat::getProvider).filter(Objects::nonNull)
                .forEach(p -> known.put(p.getId(), p));
        recent.stream().map(i -> i.getProvider()).filter(Objects::nonNull)
                .forEach(p -> known.put(p.getId(), p));
        for (var provider : known.values()) {
            String name = normalize(provider.getName());
            String code = normalize(provider.getCode());
            if ((name.length() >= 5 && wholePhrase(normalizedFilename, name)) ||
                    (code.length() >= 5 && wholePhrase(normalizedFilename, code))) {
                add(scores, provider, 55, "Nombre o código del proveedor en el archivo");
            }
        }
        // No suggestion solely from popularity; history must match a specific format.
        var candidates = scores.values().stream()
                .map(s -> new Suggestion(s.provider.getId(), s.provider.getName(), s.score, List.copyOf(s.reasons)))
                .sorted(Comparator.comparingInt(Suggestion::score).reversed())
                .limit(5).toList();
        boolean ambiguous = candidates.size() > 1 && candidates.get(0).score() - candidates.get(1).score() < 20;
        return new Suggestions(candidates, ambiguous);
    }

    private static void add(Map<Long, Score> scores, Provider provider, int points, String reason) {
        Score score = scores.computeIfAbsent(provider.getId(), id -> new Score(provider));
        score.score += points;
        if (!score.reasons.contains(reason)) score.reasons.add(reason);
    }
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
    private static boolean wholePhrase(String text, String phrase) {
        return !(text.isEmpty() || phrase.isEmpty()) && (" " + text + " ").contains(" " + phrase + " ");
    }
    private static final class Score {
        final Provider provider;
        int score;
        final List<String> reasons = new ArrayList<>();
        Score(Provider provider) { this.provider = provider; }
    }
}
