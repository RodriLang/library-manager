package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeName;
import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
public class AuthorResolver {

    private final AuthorRepository authorRepository;

    public Map<String, Author> loadAuthors(List<PriceListRow> rows) {
        Map<String, Author> authorsByName = authorRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        author -> normalizeName(author.getName()),
                        Function.identity(),
                        (existing, repeated) -> existing
                ));

        Map<String, String> originalNamesByNormalizedName = rows.stream()
                .map(PriceListRow::authorName)
                .filter(PriceListNormalizationUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toMap(
                        PriceListNormalizationUtils::normalizeName,
                        Function.identity(),
                        (existing, repeated) -> existing
                ));

        List<Author> newAuthors = originalNamesByNormalizedName.entrySet()
                .stream()
                .filter(entry -> !authorsByName.containsKey(entry.getKey()))
                .map(entry -> Author.builder()
                        .name(entry.getValue())
                        .build())
                .toList();

        authorRepository.saveAll(newAuthors)
                .forEach(author -> authorsByName.put(
                        normalizeName(author.getName()),
                        author
                ));

        return authorsByName;
    }

    public Set<Author> resolve(PriceListRow row, ImportContext context) {
        Set<Author> authors = new LinkedHashSet<>();

        if (!hasText(row.authorName())) {
            return authors;
        }

        Author author = context.authorsByName().get(normalizeName(row.authorName()));

        if (author != null) {
            authors.add(author);
        }

        return authors;
    }
}
