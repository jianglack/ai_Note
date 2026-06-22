package com.ainote.app.chunking;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构感知分块器测试
 */
class StructureAwareDocumentSplitterTest {

    private StructureAwareDocumentSplitter splitter;

    @BeforeEach
    void setUp() {
        ContentTypeDetector detector = new ContentTypeDetector();
        MarkdownStructureParser markdownParser = new MarkdownStructureParser();
        CodeStructureParser codeParser = new CodeStructureParser();
        
        splitter = new StructureAwareDocumentSplitter(detector, markdownParser, codeParser);
    }

    @Test
    @DisplayName("应正确分割 Markdown 文档")
    void shouldSplitMarkdownDocument() {
        String markdown = """
            # Java 学习笔记
            
            这是一份 Java 学习笔记。
            
            ## 第一章：基础语法
            
            Java 是一门面向对象的编程语言。它具有跨平台、安全性高等特点。
            
            ### 1.1 变量声明
            
            在 Java 中，变量需要先声明后使用。
            
            ## 第二章：集合框架
            
            Java 集合框架包括 List、Set、Map 等接口。
            """;

        Document doc = Document.from(markdown, Metadata.from("noteId", "test-123"));
        List<TextSegment> segments = splitter.split(doc);

        // 验证分块数量
        assertThat(segments).isNotEmpty();
        
        // 验证元数据
        for (TextSegment segment : segments) {
            assertThat(segment.metadata().getString("noteId")).isEqualTo("test-123");
            assertThat(segment.metadata().getString("chunkIndex")).isNotNull();
            
            // 打印调试信息
            System.out.println("=== Segment ===");
            System.out.println("Text: " + segment.text().substring(0, Math.min(50, segment.text().length())) + "...");
            System.out.println("Breadcrumb: " + segment.metadata().getString("breadcrumb"));
            System.out.println("Title: " + segment.metadata().getString("title"));
            System.out.println("Level: " + segment.metadata().getString("level"));
            System.out.println();
        }
        
        // 验证面包屑路径
        boolean hasBreadcrumb = segments.stream()
                .anyMatch(s -> s.metadata().getString("breadcrumb") != null);
        assertThat(hasBreadcrumb).isTrue();
    }

    @Test
    @DisplayName("应正确分割纯文本")
    void shouldSplitPlainText() {
        String plainText = """
            这是第一段文字。包含一些内容。
            
            这是第二段文字。也包含一些内容。
            
            这是第三段文字。继续包含内容。
            """;

        Document doc = Document.from(plainText);
        List<TextSegment> segments = splitter.split(doc);

        assertThat(segments).isNotEmpty();
        
        for (TextSegment segment : segments) {
            System.out.println("Plain text segment: " + segment.text());
        }
    }

    @Test
    @DisplayName("应正确处理代码内容")
    void shouldSplitCodeContent() {
        String code = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
                
                public void greet(String name) {
                    System.out.println("Hello, " + name);
                }
            }
            """;

        Document doc = Document.from(code);
        List<TextSegment> segments = splitter.split(doc);

        assertThat(segments).isNotEmpty();
        
        for (TextSegment segment : segments) {
            System.out.println("=== Code Segment ===");
            System.out.println(segment.text());
            System.out.println("Breadcrumb: " + segment.metadata().getString("breadcrumb"));
            System.out.println();
        }
    }

    @Test
    @DisplayName("应正确处理过大的段落")
    void shouldSplitLargeSegments() {
        // 创建一个超过 800 字符的段落
        StringBuilder largeText = new StringBuilder("# 长文档\n\n");
        for (int i = 0; i < 100; i++) {
            largeText.append("这是一段很长的文字，用于测试分块功能。");
        }

        Document doc = Document.from(largeText.toString());
        List<TextSegment> segments = splitter.split(doc);

        // 应该被分割成多个段
        assertThat(segments.size()).isGreaterThan(1);
        
        // 每个段的大小应该合理
        for (TextSegment segment : segments) {
            assertThat(segment.text().length()).isLessThanOrEqualTo(1000);
        }
    }

    @Test
    @DisplayName("应正确检测内容类型")
    void shouldDetectContentType() {
        ContentTypeDetector detector = new ContentTypeDetector();

        // Markdown - 使用更长的内容以确保检测准确
        String markdown = """
            # Title
            
            ## Subtitle
            
            - List item 1
            - List item 2
            
            Content with [link](url) and more text to make it longer.
            
            ### Another section
            
            More content here to ensure proper detection.
            """;
        ContentType markdownType = detector.detect(markdown);
        // Markdown 或 MIXED 都是可接受的
        assertThat(markdownType).isIn(ContentType.MARKDOWN, ContentType.MIXED);

        // 代码
        String code = "public class Test { public void method() {} }";
        assertThat(detector.detect(code)).isEqualTo(ContentType.CODE);

        // 纯文本
        String plainText = "这是一段普通的文字。没有特殊格式。";
        assertThat(detector.detect(plainText)).isEqualTo(ContentType.PLAIN_TEXT);
    }
}
