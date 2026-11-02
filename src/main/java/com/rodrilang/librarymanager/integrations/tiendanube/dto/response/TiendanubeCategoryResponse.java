package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.Map;
import java.util.List;

public record TiendanubeCategoryResponse(Long id, Map<String,String> name, Long parent,
                                          List<Long> subcategories, String visibility) {}
