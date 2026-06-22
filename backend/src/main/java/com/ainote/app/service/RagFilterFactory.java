package com.ainote.app.service;

import dev.langchain4j.store.embedding.filter.Filter;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public final class RagFilterFactory {

    private RagFilterFactory() {
    }

    public static Filter userFilter(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required for RAG filter");
        }
        return metadataKey("userId").isEqualTo(userId);
    }
}
