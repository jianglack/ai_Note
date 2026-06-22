package com.ainote.app.agent.pending;

import java.util.Arrays;
import java.util.Optional;

enum PendingActionType {
    DELETE_NOTE("noteId"),
    PERMANENT_DELETE("noteId"),
    EMPTY_TRASH("count"),
    DELETE_SCHEDULE("scheduleId"),
    DELETE_FOLDER("folderId");

    private final String[] requiredFields;

    PendingActionType(String... requiredFields) {
        this.requiredFields = requiredFields;
    }

    String[] requiredFields() {
        return requiredFields;
    }

    static Optional<PendingActionType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equals(value))
                .findFirst();
    }
}
