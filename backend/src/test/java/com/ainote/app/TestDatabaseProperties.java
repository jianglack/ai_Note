package com.ainote.app;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;

final class TestDatabaseProperties {

    private static final String TEMP_DB_FLAG = "AINOTE_IT_TEMP_DB";
    private static final String ADMIN_URL = "AINOTE_IT_JDBC_ADMIN_URL";
    private static final String USERNAME = "AINOTE_IT_JDBC_USERNAME";
    private static final String PASSWORD = "AINOTE_IT_JDBC_PASSWORD";

    private TestDatabaseProperties() {
    }

    static Database database(String imageName, String databaseName) {
        return new Database(imageName, databaseName);
    }

    private static void requireTempDatabaseFlag() {
        if (!"true".equalsIgnoreCase(value(TEMP_DB_FLAG))) {
            throw new IllegalStateException(
                    ADMIN_URL + " is only allowed when " + TEMP_DB_FLAG + "=true, to avoid touching a real database");
        }
    }

    private static void createFreshDatabase(String adminUrl, String username, String password, String databaseName) {
        try (var connection = DriverManager.getConnection(withStringType(adminUrl), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quotedIdentifier(databaseName) + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + quotedIdentifier(databaseName));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create temporary test database " + databaseName, e);
        }
    }

    private static String jdbcUrlForDatabase(String adminUrl, String databaseName) {
        int queryIndex = adminUrl.indexOf('?');
        String base = queryIndex >= 0 ? adminUrl.substring(0, queryIndex) : adminUrl;
        String query = queryIndex >= 0 ? adminUrl.substring(queryIndex) : "";
        int slash = base.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL: " + adminUrl);
        }
        return withStringType(base.substring(0, slash + 1) + databaseName + query);
    }

    private static String quotedIdentifier(String identifier) {
        if (!identifier.toLowerCase(Locale.ROOT).matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe database name: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private static String withStringType(String url) {
        return url.contains("?") ? url + "&stringtype=unspecified" : url + "?stringtype=unspecified";
    }

    private static String required(String name) {
        String value = value(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static String value(String name) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(name);
        return env == null || env.isBlank() ? null : env;
    }

    static final class Database {
        private final String imageName;
        private final String databaseName;
        private volatile StartedDatabase started;

        private Database(String imageName, String databaseName) {
            this.imageName = imageName;
            this.databaseName = databaseName;
        }

        String jdbcUrl() {
            return started().jdbcUrl();
        }

        String username() {
            return started().username();
        }

        String password() {
            return started().password();
        }

        private StartedDatabase started() {
            StartedDatabase current = started;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                current = started;
                if (current == null) {
                    current = start();
                    started = current;
                }
                return current;
            }
        }

        private StartedDatabase start() {
            String adminUrl = value(ADMIN_URL);
            if (adminUrl != null) {
                requireTempDatabaseFlag();
                String username = required(USERNAME);
                String password = required(PASSWORD);
                createFreshDatabase(adminUrl, username, password, databaseName);
                return new StartedDatabase(jdbcUrlForDatabase(adminUrl, databaseName), username, password, null);
            }

            Assumptions.assumeTrue(dockerAvailable(), "Docker is not available for Testcontainers-backed tests");
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(imageName)
                    .withDatabaseName(databaseName)
                    .withUsername("ainote")
                    .withPassword("ainote");
            postgres.start();
            return new StartedDatabase(
                    withStringType(postgres.getJdbcUrl()),
                    postgres.getUsername(),
                    postgres.getPassword(),
                    postgres);
        }
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean adminDatabaseConfigured() {
        return value(ADMIN_URL) != null;
    }

    private record StartedDatabase(
            String jdbcUrl,
            String username,
            String password,
            PostgreSQLContainer<?> postgres) {
    }
}
