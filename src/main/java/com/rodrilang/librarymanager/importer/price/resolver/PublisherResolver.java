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
        Map<String, Publisher> publishersByName = loadPublishersByNormalizedName();

        Map<String, String> missingNames = rows.stream()
                .map(PriceListRow::publisherName)
                .filter(PriceListNormalizationUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toMap(
                        PriceListNormalizationUtils::normalizeName,
                        PriceListNormalizationUtils::formatName,
                        (existing, repeated) -> existing
                ));

        missingNames.entrySet().stream()
                .filter(entry -> !publishersByName.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(publisherRepository::insertIfAbsent);

        return loadPublishersByNormalizedName();
    }

    public Publisher resolve(PriceListRow row, ImportContext context) {
        if (!hasText(row.publisherName())) {
            return null;
        }

        return context.publishersByName().get(normalizeName(row.publisherName()));
    }

    private Map<String, Publisher> loadPublishersByNormalizedName() {
        return publisherRepository.findAll().stream()
                .collect(Collectors.toMap(
                        publisher -> normalizeName(publisher.getName()),
                        Function.identity(),
                        (existing, repeated) -> existing
                ));
    }
}