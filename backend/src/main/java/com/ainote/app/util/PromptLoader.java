package com.ainote.app.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词加载器
 * 从 classpath:prompts/ 目录加载提示词模板，带缓存
 */
@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final String PROMPT_DIR = "prompts/";
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 加载提示词模板
     * @param name 文件名（不含路径前缀），如 "suggest-tags.txt"
     * @return 提示词内容
     */
    public String load(String name) {
        return cache.computeIfAbsent(name, this::doLoad);
    }

    /**
     * 加载并格式化提示词（使用 String.format）
     * @param name 文件名
     * @param args 格式化参数
     * @return 格式化后的提示词
     */
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
