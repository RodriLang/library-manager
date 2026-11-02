package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;
import java.util.Map;

public record TiendanubeProductResponse(

        Long id,

        Map<String, String> name,

        Map<String, String> description,

        Boolean published,

        List<TiendanubeVariantResponse> variants,

        List<TiendanubeImageResponse> images,
        List<com.fasterxml.jackson.databind.JsonNode> categories

) {
    public TiendanubeProductResponse(Long id, Map<String,String> name, Map<String,String> description,
                                      Boolean published, List<TiendanubeVariantResponse> variants,
                                      List<TiendanubeImageResponse> images) {
        this(id, name, description, published, variants, images, null);
    }
}