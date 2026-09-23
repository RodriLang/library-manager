package com.rodrilang.librarymanager.editorialprice.event;

import java.util.Set;

public record EffectiveEditorialPriceChangedEvent(Set<Long> bookIds) {
}
