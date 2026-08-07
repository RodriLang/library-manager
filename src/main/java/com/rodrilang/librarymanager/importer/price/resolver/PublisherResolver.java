package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.util.TextNormalizer.normalizeForMatch;
import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
public class PublisherResolver {

    private final PublisherRepository publisherRepository;

    public Map<String, Publisher> loadPublishers(
            List<PriceListRow> rows
    ) {
        Map<String, String> originalNamesByNormalizedName =
                rows.stream()
                        .map(PriceListRow::publisherName)
                        .filter(
                                PriceListNormalizationUtils::hasText
                        )
                        .map(String::trim)
                        .collect(
                                Collectors.toMap(
                                        TextNormalizer::normalizeForMatch,
                                        PriceListNormalizationUtils::formatName,
                                        (existing, repeated) -> existing
                                )
                        );

        if (originalNamesByNormalizedName.isEmpty()) {
            return new HashMap<>();
        }

        Set<String> normalizedNames =
                originalNamesByNormalizedName.keySet();

        Map<String, Publisher> publishersByName =
                publisherRepository
                        .findByNameNormalizedIn(
                                normalizedNames
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Publisher::getNameNormalized,
                                        Function.identity(),
                                        (existing, repeated) -> existing,
                                        HashMap::new
                                )
                        );

        List<Publisher> newPublishers =
                originalNamesByNormalizedName
                        .entrySet()
                        .stream()
                        .filter(entry ->
                                !publishersByName.containsKey(
                                        entry.getKey()
                                )
                        )
                        .map(entry ->
                                Publisher.builder()
                                        .name(entry.getValue())
                                        .build()
                        )
                        .toList();

        if (!newPublishers.isEmpty()) {
            publisherRepository
                    .saveAll(newPublishers)
                    .forEach(publisher ->
                            publishersByName.put(
                                    publisher.getNameNormalized(),
                                    publisher
                            )
                    );
        }

        return publishersByName;
    }

    public Publisher resolve(
            PriceListRow row,
            ImportContext context
    ) {
        if (!hasText(row.publisherName())) {
            return null;
        }

        return context.publishersByName()
                .get(
                        normalizeForMatch(
                                row.publisherName()
                        )
                );
    }
}