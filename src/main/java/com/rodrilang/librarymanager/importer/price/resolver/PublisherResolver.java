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
    private final ProviderPublisherMappingRepository mappingRepository;

    public Map<String, Publisher> loadPublishers(
            Long providerId,
            List<PriceListRow> rows
    ) {
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

        if (originalNamesByNormalizedName.isEmpty()) {
            return new HashMap<>();
        }

        Set<String> normalizedNames =
                originalNamesByNormalizedName.keySet();

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

        Map<String, Publisher> publishersByName =
                new HashMap<>();

        Set<String> normalizedNamesWithoutMapping =
                normalizedNames.stream()
                        .filter(normalizedName ->
                                !mappingsByExternalName.containsKey(normalizedName)
                        )
                        .collect(Collectors.toSet());

        mappingsByExternalName.forEach(
                (externalNameNormalized, mapping) -> {

                    if (mapping.getResolutionType() == ProviderPublisherMappingType.MAP) {

                        if (mapping.getPublisher() == null) {
                            throw new IllegalStateException(
                                    "Provider publisher mapping %d is MAP but has no publisher"
                                            .formatted(mapping.getId())
                            );
                        }

                        publishersByName.put(externalNameNormalized, mapping.getPublisher());
                    }
                }
        );

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

        return context.publishersByName().get(normalizeForMatch(row.publisherName()));
    }
}