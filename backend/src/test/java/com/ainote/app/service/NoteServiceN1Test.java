package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoteServiceN1Test {

    private static final Path REPOSITORY_FILE = Path.of(
            "src", "main", "java", "com", "ainote", "app", "repository", "NoteRepository.java");

    @Test
    void repositoryHasFetchJoinForTagsAndFolder() throws Exception {
        String content = Files.readString(REPOSITORY_FILE);

        assertThat(content)
                .contains("findByUserIdWithTagsAndFolder")
                .contains("LEFT JOIN FETCH n.tags")
                .contains("LEFT JOIN FETCH n.folder");
    }
}
