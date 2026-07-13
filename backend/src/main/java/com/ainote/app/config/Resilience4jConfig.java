package com.ainote.app.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CommonCircuitBreakerConfigurationProperties;
import io.github.resilience4j.common.retry.configuration.CommonRetryConfigurationProperties;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j registry configuration that remains compatible with Java 17 and Spring Boot 4.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
public class Resilience4jConfig {

    @Bean
    @ConfigurationProperties(prefix = "resilience4j.circuitbreaker")
    CommonCircuitBreakerConfigurationProperties circuitBreakerProperties() {
        return new CommonCircuitBreakerConfigurationProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "resilience4j.retry")
    CommonRetryConfigurationProperties retryProperties() {
        return new CommonRetryConfigurationProperties();
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(
            CommonCircuitBreakerConfigurationProperties properties,
            ObjectProvider<CircuitBreakerConfigCustomizer> customizers) {
        CompositeCustomizer<CircuitBreakerConfigCustomizer> compositeCustomizer =
                new CompositeCustomizer<>(customizers.orderedStream().toList());
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        properties.getInstances().forEach((name, instanceProperties) ->
                registry.circuitBreaker(name,
                        properties.createCircuitBreakerConfig(name, instanceProperties, compositeCustomizer)));
        return registry;
    }

    @Bean
    RetryRegistry retryRegistry(
            CommonRetryConfigurationProperties properties,
            ObjectProvider<RetryConfigCustomizer> customizers) {
        CompositeCustomizer<RetryConfigCustomizer> compositeCustomizer =
                new CompositeCustomizer<>(customizers.orderedStream().toList());
        RetryRegistry registry = RetryRegistry.ofDefaults();
        properties.getInstances().keySet().forEach(name ->
                registry.retry(name, properties.createRetryConfig(name, compositeCustomizer)));
        return registry;
    }

    @Bean
    TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
    }

    @Bean
    TaggedRetryMetrics retryMetrics(RetryRegistry registry) {
        return TaggedRetryMetrics.ofRetryRegistry(registry);
    }
}
