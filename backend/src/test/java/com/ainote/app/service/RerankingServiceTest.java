package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RerankingService 测试
 */
class RerankingServiceTest {

    private RerankingService rerankingService;

    @BeforeEach
    void setUp() {
        rerankingService = new RerankingService(new ObjectMapper());
        ReflectionTestUtils.setField(rerankingService, "enabled", true);
        ReflectionTestUtils.setField(rerankingService, "useCohere", false);
    }

    @Test
    @DisplayName("测试简单重排序 - 关键词匹配")
    void testSimpleRerank() {
        List<Note> candidates = Arrays.asList(
            createNote("note-1", "Java 基础", "Java 是一门编程语言"),
            createNote("note-2", "Python 教程", "Python 是一门编程语言"),
            createNote("note-3", "Java 多线程", "Java 多线程编程详解")
        );

        List<Note> reranked = rerankingService.rerank("Java 多线程", candidates, 2);
        
        assertEquals(2, reranked.size());
        assertEquals("note-3", reranked.get(0).getId());
    }

    @Test
    @DisplayName("测试重排序 - 标题权重")
    void testRerankWithTitleWeight() {
        List<Note> candidates = Arrays.asList(
            createNote("note-1", "其他主题", "Java 是一门编程语言"),
            createNote("note-2", "Java 教程", "这是一些其他内容")
        );

        List<Note> reranked = rerankingService.rerank("Java", candidates, 2);
        
        assertEquals(2, reranked.size());
        assertEquals("note-2", reranked.get(0).getId());
    }

    @Test
    @DisplayName("测试重排序 - 标签匹配")
    void testRerankWithTagMatch() {
        Note note1 = createNote("note-1", "笔记1", "内容1");
        Note note2 = createNote("note-2", "笔记2", "内容2");
        note2.setTags(new HashSet<>(Arrays.asList(createTag("Java"))));

        List<Note> candidates = Arrays.asList(note1, note2);

        List<Note> reranked = rerankingService.rerank("Java", candidates, 2);
        
        assertEquals(2, reranked.size());
        assertEquals("note-2", reranked.get(0).getId());
    }

    @Test
    @DisplayName("测试重排序 - TopN 限制")
    void testRerankWithTopN() {
        List<Note> candidates = Arrays.asList(
            createNote("note-1", "Java 1", "Java 内容"),
            createNote("note-2", "Java 2", "Java 内容"),
            createNote("note-3", "Java 3", "Java 内容"),
            createNote("note-4", "Java 4", "Java 内容"),
            createNote("note-5", "Java 5", "Java 内容")
        );

        List<Note> reranked = rerankingService.rerank("Java", candidates, 3);
        
        assertEquals(3, reranked.size());
    }

    @Test
    @DisplayName("测试重排序 - 空列表")
    void testRerankEmptyList() {
        List<Note> candidates = new ArrayList<>();
        
        List<Note> reranked = rerankingService.rerank("Java", candidates, 5);
        
        assertTrue(reranked.isEmpty());
    }

    @Test
    @DisplayName("测试重排序 - 空查询")
    void testRerankEmptyQuery() {
        List<Note> candidates = Arrays.asList(
            createNote("note-1", "Java", "Java 内容")
        );

        List<Note> reranked = rerankingService.rerank("", candidates, 5);
        
        assertEquals(1, reranked.size());
    }

    @Test
    @DisplayName("测试重排序统计")
    void testRerankingStats() {
        List<Note> original = Arrays.asList(
            createNote("note-1", "Java 1", "Java"),
            createNote("note-2", "Java 2", "Java"),
            createNote("note-3", "Java 3", "Java")
        );

        List<Note> reranked = original.subList(0, 2);
        
        var stats = rerankingService.getStats(original, reranked, "simple");
        
        assertEquals(3, stats.originalCount());
        assertEquals(2, stats.rerankedCount());
        assertEquals("simple", stats.method());
        assertTrue(stats.reductionPercentage() > 0);
    }

    @Test
    @DisplayName("测试重排序 - 多关键词")
    void testRerankMultipleKeywords() {
        List<Note> candidates = Arrays.asList(
            createNote("note-1", "Java", "Java 编程"),
            createNote("note-2", "Python", "Python 编程"),
            createNote("note-3", "Java Python", "Java 和 Python 编程")
        );

        List<Note> reranked = rerankingService.rerank("Java Python", candidates, 3);
        
        assertEquals(3, reranked.size());
        assertEquals("note-3", reranked.get(0).getId());
    }

    private Note createNote(String id, String title, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        return note;
    }

    private Tag createTag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        return tag;
    }
}
