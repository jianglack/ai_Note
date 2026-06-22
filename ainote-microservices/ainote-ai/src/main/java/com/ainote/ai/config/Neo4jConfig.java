package com.ainote.ai.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.neo4j", name = "uri")
    public Driver neo4jDriver(
            @Value("${app.neo4j.uri}") String uri,
            @Value("${app.neo4j.user}") String user,
            @Value("${app.neo4j.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }
}
