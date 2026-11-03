package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import java.util.List;
import java.util.Map;

public record TiendanubeCreateProductRequest(

        Map<String, String> name,

        Map<String, String> description,

        List<TiendanubeCreateVariantRequest> variants,

        List<TiendanubeCreateImageRequest> images,

        Boolean published,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<Long> categories

) {
}