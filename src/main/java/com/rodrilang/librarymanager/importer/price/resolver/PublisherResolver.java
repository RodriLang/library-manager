package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.importer.price.dto.internal.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.enums.ProviderPublisherMappingType;
import com.rodrilang.librarymanager.importer.price.model.ProviderPublisherMapping;
import com.rodrilang.librarymanager.importer.price.repository.ProviderPublisherMappingRepository;
import com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.util.TextNormalizer.normalizeForMatch;
import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublisherResolver {

    private final PublisherRepository publisherRepository;
    private final ProviderPublisherMappingRepository mappingRepository;

    public Map<String, Publisher> loadPublishers(
            Long providerId,
            List<PriceListRow> rows
    ) {
        long startedAt = System.nanoTime();

        long stepStartedAt = System.nanoTime();

        Map<String, String> originalNamesByNormalizedName =
                rows.stream()
                        .map(PriceListRow::publisherName)
                        .filter(PriceListNormalizationUtils::hasText)
                        .map(String::trim)
                        .collect(
                                Collectors.toMap(
                                        TextNormalizer::normalizeForMatch,
                                        PriceListNormalizationUtils::formatName,
                                        (existing, repeated) -> existing
                                )
                        );

        long namesPreparationMs =
                elapsedMillis(stepStartedAt);

        if (originalNamesByNormalizedName.isEmpty()) {
            long totalMs = elapsedMillis(startedAt);

            log.info(
                    "Publisher resolver timing. "
                            + "providerId={} "
                            + "names=0 "
                            + "mappings=0 "
                            + "mappedPublishers=0 "
                            + "withoutMapping=0 "
                            + "newPublishers=0 "
                            + "namesPreparation={}ms "
                            + "mappingLookup=0ms "
                            + "mappingResolution=0ms "
                            + "publisherLookup=0ms "
                            + "newPublishersPreparation=0ms "
                            + "newPublishersPersistence=0ms "
                            + "other={}ms "
                            + "total={}ms",
                    providerId,
                    namesPreparationMs,
                    Math.max(0L, totalMs - namesPreparationMs),
                    totalMs
            );

            return new HashMap<>();
        }

        Set<String> normalizedNames =
                originalNamesByNormalizedName.keySet();

        stepStartedAt = System.nanoTime();

        Map<String, ProviderPublisherMapping> mappingsByExternalName =
                mappingRepository
                        .findByProviderIdAndExternalNameNormalizedIn(
                                providerId,
                                normalizedNames
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ProviderPublisherMapping::getExternalNameNormalized,
                                        Function.identity()
                                )
                        );

        long mappingLookupMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        Map<String, Publisher> publishersByName = new HashMap<>();

        Set<String> normalizedNamesWithoutMapping =
                normalizedNames.stream()
                        .filter(normalizedName ->
                                !mappingsByExternalName.containsKey(normalizedName)
                        )
                        .collect(Collectors.toSet());

        int mappedPublishers = 0;

        for (Map.Entry<String, ProviderPublisherMapping> entry
                : mappingsByExternalName.entrySet()) {

            ProviderPublisherMapping mapping = entry.getValue();

            if (mapping.getResolutionType() != ProviderPublisherMappingType.MAP) {
                continue;
            }

            if (mapping.getPublisher() == null) {
                throw new IllegalStateException(
                        "Provider publisher mapping %d is MAP but has no publisher"
                                .formatted(mapping.getId())
                );
            }

            publishersByName.put(entry.getKey(), mapping.getPublisher());

            mappedPublishers++;
        }

        long mappingResolutionMs =
                elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        if (!normalizedNamesWithoutMapping.isEmpty()) {
            publisherRepository
                    .findByNameNormalizedIn(normalizedNamesWithoutMapping)
                    .forEach(publisher ->
                            publishersByName.put(
                                    publisher.getNameNormalized(),
                                    publisher
                            )
                    );
        }

        long publisherLookupMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

        List<Publisher> newPublishers =
                normalizedNamesWithoutMapping.stream()
                        .filter(normalizedName ->
                                !publishersByName.containsKey(normalizedName)
                        )
                        .map(normalizedName ->
                                Publisher.builder()
                                        .name(
                                                originalNamesByNormalizedName
                                                        .get(normalizedName)
                                        )
                                        .build()
                        )
                        .toList();

        long newPublishersPreparationMs = elapsedMillis(stepStartedAt);

        stepStartedAt = System.nanoTime();

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

        long newPublishersPersistenceMs = elapsedMillis(stepStartedAt);

        long totalMs = elapsedMillis(startedAt);

        long measuredMs =
                namesPreparationMs
                        + mappingLookupMs
                        + mappingResolutionMs
                        + publisherLookupMs
                        + newPublishersPreparationMs
                        + newPublishersPersistenceMs;

        log.info(
                "Publisher resolver timing. "
                        + "providerId={} "
                        + "names={} "
                        + "mappings={} "
                        + "mappedPublishers={} "
                        + "withoutMapping={} "
                        + "newPublishers={} "
                        + "namesPreparation={}ms "
                        + "mappingLookup={}ms "
                        + "mappingResolution={}ms "
                        + "publisherLookup={}ms "
                        + "newPublishersPreparation={}ms "
                        + "newPublishersPersistence={}ms "
                        + "other={}ms "
                        + "total={}ms",
                providerId,
                normalizedNames.size(),
                mappingsByExternalName.size(),
                mappedPublishers,
                normalizedNamesWithoutMapping.size(),
                newPublishers.size(),
                namesPreparationMs,
                mappingLookupMs,
                mappingResolutionMs,
                publisherLookupMs,
                newPublishersPreparationMs,
                newPublishersPersistenceMs,
                Math.max(
                        0L,
                        totalMs - measuredMs
                ),
                totalMs
        );

        return publishersByName;
    }

    public Publisher resolve(
            PriceListRow row,
            ImportContext context
    ) {
        if (!hasText(row.publisherName())) {
            return null;
        }

        return context.publishersByName().get(normalizeForMatch(row.publisherName()));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}