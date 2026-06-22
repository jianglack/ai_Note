package com.ainote.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final String PROMPT_DIR = "prompts/";
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, this::doLoad);
    }

    public String format(String name, Object... args) {
        return String.format(load(name), args);
    }

    private String doLoad(String name) {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_DIR + name);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded prompt template: {}", name);
            return content;
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", name, e);
            throw new RuntimeException("Cannot load prompt: " + name, e);
        }
    }
}
