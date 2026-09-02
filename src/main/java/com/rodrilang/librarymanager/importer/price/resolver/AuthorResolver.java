package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.repository.AuthorBatchRepository;
import com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.util.TextNormalizer.normalizeForMatch;
import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
public class AuthorResolver {

    private final AuthorRepository authorRepository;
    private final AuthorBatchRepository authorBatchRepository;

    public Map<String, Author> loadAuthors(List<PriceListRow> rows) {

        Map<String, String> originalNamesByNormalizedName =
                rows.stream()
                        .map(PriceListRow::authorName)
                        .filter(PriceListNormalizationUtils::hasText)
                        .map(String::trim)
                        .collect(
                                Collectors.toMap(
                                        TextNormalizer::normalizeForMatch,
                                        Function.identity(),
                                        (existing, repeated) -> existing
                                )
                        );

        if (originalNamesByNormalizedName.isEmpty()) {
            return new HashMap<>();
        }

        Set<String> normalizedNames = originalNamesByNormalizedName.keySet();

        Map<String, Author> authorsByName = loadExistingAuthors(normalizedNames);

        Map<String, String> missingAuthors =
                originalNamesByNormalizedName
                        .entrySet()
                        .stream()
                        .filter(entry ->
                                !authorsByName.containsKey(
                                        entry.getKey()
                                )
                        )
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue
                                )
                        );

        if (missingAuthors.isEmpty()) {
            return authorsByName;
        }

        authorBatchRepository.insertIfAbsentBatch(missingAuthors);

        return loadExistingAuthors(normalizedNames);
    }

    public Set<Author> resolve(PriceListRow row, ImportContext context) {

        Set<Author> authors = new LinkedHashSet<>();

        if (!hasText(row.authorName())) {
            return authors;
        }

        Author author = context.authorsByName().get(normalizeForMatch(row.authorName()));

        if (author != null) {
            authors.add(author);
        }

        return authors;
    }

    private Map<String, Author> loadExistingAuthors(Set<String> normalizedNames) {

        return authorRepository
                .findByNameNormalizedIn(
                        normalizedNames
                )
                .stream()
                .collect(
                        Collectors.toMap(
                                Author::getNameNormalized,
                                Function.identity(),
                                (existing, repeated) -> existing,
                                HashMap::new
                        )
                );
    }
}