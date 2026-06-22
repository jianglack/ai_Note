package com.ainote.search.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.milvus.enabled", havingValue = "true")
public class MilvusConfig {
}
