package com.ainote.app.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RagFilterFactory unit tests")
class RagFilterFactoryTest {

    @Test
    @DisplayName("userFilter: valid userId matches current user")
    void userFilter_validUserId_matchesCurrentUser() {
        Filter filter = RagFilterFactory.userFilter("user-123");
        assertThat(filter).isNotNull();
        assertThat(filter.test(Metadata.from("userId", "user-123"))).isTrue();
    }

    @Test
    @DisplayName("userFilter: does not match other users")
    void userFilter_doesNotMatchOtherUser() {
        Filter filter = RagFilterFactory.userFilter("user-123");
        assertThat(filter.test(Metadata.from("userId", "other-user"))).isFalse();
    }

    @Test
    @DisplayName("userFilter: null userId throws IllegalArgumentException")
    void userFilter_nullUserId_throws() {
        assertThatThrownBy(() -> RagFilterFactory.userFilter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId is required");
    }

    @Test
    @DisplayName("userFilter: blank userId throws IllegalArgumentException")
    void userFilter_blankUserId_throws() {
        assertThatThrownBy(() -> RagFilterFactory.userFilter("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId is required");
    }
}
