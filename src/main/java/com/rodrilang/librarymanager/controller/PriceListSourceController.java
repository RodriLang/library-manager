package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/price-list-sources")
@RequiredArgsConstructor
@Tag(name = "Proveedores" )
public class PriceListSourceController {

    @GetMapping
    public ResponseEntity<List<PriceListSource>> getAll() {
        return ResponseEntity.ok(List.of(PriceListSource.values()));
    }
}
