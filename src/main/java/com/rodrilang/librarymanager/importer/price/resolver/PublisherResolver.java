package com.rodrilang.librarymanager.importer.price.resolver;

import com.rodrilang.librarymanager.importer.price.dto.ImportContext;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeName;
import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
public class PublisherResolver {

    private final PublisherRepository publisherRepository;

    public Map<String, Publisher> loadPublishers(List<PriceListRow> rows) {
        Map<String, Publisher> publishersByName = publisherRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        publisher -> normalizeName(publisher.getName()),
                        Function.identity(),
                        (existing, repeated) -> existing
                ));

        Map<String, String> originalNamesByNormalizedName = rows.stream()
                .map(PriceListRow::publisherName)
                .filter(PriceListNormalizationUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toMap(
                        PriceListNormalizationUtils::normalizeName,
                        PriceListNormalizationUtils::formatName,
                        (existing, repeated) -> existing
                ));

        List<Publisher> newPublishers = originalNamesByNormalizedName.entrySet()
                .stream()
                .filter(entry -> !publishersByName.containsKey(entry.getKey()))
                .map(entry -> Publisher.builder()
                        .name(entry.getValue())
                        .build())
                .toList();

        publisherRepository.saveAll(newPublishers)
                .forEach(publisher -> publishersByName.put(
                        normalizeName(publisher.getName()),
                        publisher
                ));

        return publishersByName;
    }

    public Publisher resolve(PriceListRow row, ImportContext context) {
        if (!hasText(row.publisherName())) {
            return null;
        }

        return context.publishersByName().get(normalizeName(row.publisherName()));
    }
}
