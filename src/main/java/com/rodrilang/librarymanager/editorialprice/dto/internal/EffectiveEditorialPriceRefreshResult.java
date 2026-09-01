package com.rodrilang.librarymanager.editorialprice.dto.internal;

import java.util.Set;

public record EffectiveEditorialPriceRefreshResult(
        Set<Long> changedBookIds,
        Set<Long> conflictedBookIds
) {
}