package com.ainote.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DockerAvailableConditionTest {

    private final DockerAvailableCondition condition = new DockerAvailableCondition();
    private String previousTempDb;
    private String previousAdminUrl;
    private String previousUsername;
    private String previousPassword;

    @BeforeEach
    void rememberProperties() {
        previousTempDb = System.getProperty("AINOTE_IT_TEMP_DB");
        previousAdminUrl = System.getProperty("AINOTE_IT_JDBC_ADMIN_URL");
        previousUsername = System.getProperty("AINOTE_IT_JDBC_USERNAME");
        previousPassword = System.getProperty("AINOTE_IT_JDBC_PASSWORD");
    }

    @AfterEach
    void restoreProperties() {
        restore("AINOTE_IT_TEMP_DB", previousTempDb);
        restore("AINOTE_IT_JDBC_ADMIN_URL", previousAdminUrl);
        restore("AINOTE_IT_JDBC_USERNAME", previousUsername);
        restore("AINOTE_IT_JDBC_PASSWORD", previousPassword);
    }

    @Test
    void enablesWhenExplicitTemporaryAdminDatabaseIsConfigured() {
        System.setProperty("AINOTE_IT_TEMP_DB", "true");
        System.setProperty("AINOTE_IT_JDBC_ADMIN_URL", "jdbc:postgresql://localhost:5432/postgres");
        System.setProperty("AINOTE_IT_JDBC_USERNAME", "ainote");
        System.setProperty("AINOTE_IT_JDBC_PASSWORD", "ainote_dev_password");

        var result = condition.evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isFalse();
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
