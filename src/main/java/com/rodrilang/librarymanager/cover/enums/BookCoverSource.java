package com.rodrilang.librarymanager.cover.enums;

public enum BookCoverSource {

    MANUAL_UPLOAD(100),
    PUBLISHER(90),
    PRICE_LIST(70),
    EXTERNAL_PROVIDER(50);

    private final int priority;

    BookCoverSource(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public boolean hasHigherPriorityThan(BookCoverSource other) {
        if (other == null) {
            return true;
        }

        return this.priority > other.priority;
    }

    public boolean hasEqualOrHigherPriorityThan(BookCoverSource other) {
        if (other == null) {
            return true;
        }

        return this.priority >= other.priority;
    }
}