package com.ainote.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application startup")
@RequiresDocker
class AiNoteApplicationTest {

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg16", "ainote_boot");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.neo4j.enabled", () -> "false");
    }

    @Test
    @DisplayName("loads Spring context after Flyway migrations")
    void contextLoads() {
    }

}
