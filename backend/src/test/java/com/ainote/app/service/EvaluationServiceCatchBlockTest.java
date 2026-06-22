package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationServiceCatchBlockTest {

    private static final Path SOURCE_FILE = Path.of(
            "src", "main", "java", "com", "ainote", "app", "service", "EvaluationService.java");

    @Test
    void noEmptyCatchBlocks() throws Exception {
        String content = Files.readString(SOURCE_FILE);

        assertThat(content)
                .doesNotContain("catch (Exception ignored)")
                .doesNotContain("catch(Exception ignored)");
    }

    @Test
    void warnLoggingPresent() throws Exception {
        String content = Files.readString(SOURCE_FILE);

        assertThat(content)
                .contains("log.warn(\"Failed to serialize actualContexts: {}\", e.getMessage())");

        long detailsCount = content.lines()
                .filter(line -> line.contains("Failed to serialize evaluationDetails"))
                .count();
        assertThat(detailsCount)
                .as("Expected 2 occurrences of 'Failed to serialize evaluationDetails' warn log")
                .isEqualTo(2);
    }
}
