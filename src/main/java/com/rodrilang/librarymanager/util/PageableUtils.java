package com.rodrilang.librarymanager.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

public final class PageableUtils {

    private PageableUtils() {
    }

    public static Pageable mapSortProperties(
            Pageable pageable,
            Map<String, String> propertyMappings
    ) {

        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = propertyMappings.getOrDefault(
                            order.getProperty(),
                            order.getProperty()
                    );

                    return new Sort.Order(
                            order.getDirection(),
                            property
                    );
                })
                .toList();

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(orders)
        );
    }
}