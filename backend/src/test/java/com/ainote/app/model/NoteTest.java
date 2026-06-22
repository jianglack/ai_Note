package com.ainote.app.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NoteTest {

    private Note note;

    @BeforeEach
    void setUp() {
        note = new Note();
    }

    @Test
    @DisplayName("测试默认构造方法")
    void shouldCreateNoteWithDefaultConstructor() {
        assertNotNull(note);
        assertNull(note.getId());
        assertNull(note.getTitle());
        assertNull(note.getContent());
        assertNull(note.getCreatedAt());
        assertNull(note.getUpdatedAt());
        assertNull(note.getDeletedAt());
        assertNull(note.getFolderId());
        assertTrue(note.getTags().isEmpty());
    }

    @Test
    @DisplayName("测试属性设置和获取")
    void shouldSetAndGetNoteProperties() {
        // 设置属性
        note.setId("note-123");
        note.setTitle("Test Note");
        note.setContent("This is a test note");
        String createdAt = "2026-04-08";
        note.setCreatedAt(createdAt);
        String updatedAt = "2026-04-08";
        note.setUpdatedAt(updatedAt);
        String deletedAt = "2026-04-08";
        note.setDeletedAt(deletedAt);
        note.setFolderId("folder-123");

        // 验证属性
        assertEquals("note-123", note.getId());
        assertEquals("Test Note", note.getTitle());
        assertEquals("This is a test note", note.getContent());
        assertEquals(createdAt, note.getCreatedAt());
        assertEquals(updatedAt, note.getUpdatedAt());
        assertEquals(deletedAt, note.getDeletedAt());
        assertEquals("folder-123", note.getFolderId());
    }

    @Test
    @DisplayName("测试标签列表操作")
    void shouldHandleTagsList() {
        // 创建标签
        Tag tag1 = new Tag();
        tag1.setId("tag-1");
        tag1.setName("Tag 1");

        Tag tag2 = new Tag();
        tag2.setId("tag-2");
        tag2.setName("Tag 2");

        // 添加标签
        List<Tag> tags = new ArrayList<>();
        tags.add(tag1);
        tags.add(tag2);
        note.setTags(tags);

        // 验证标签
        assertEquals(2, note.getTags().size());
        assertEquals("tag-1", note.getTags().get(0).getId());
        assertEquals("Tag 1", note.getTags().get(0).getName());
        assertEquals("tag-2", note.getTags().get(1).getId());
        assertEquals("Tag 2", note.getTags().get(1).getName());

        // 清空标签
        note.getTags().clear();
        assertTrue(note.getTags().isEmpty());
    }

    @Test
    @DisplayName("测试toString方法")
    void shouldGenerateStringRepresentation() {
        note.setId("note-123");
        note.setTitle("Test Note");
        String toString = note.toString();
        assertNotNull(toString);
    }

    @Test
    @DisplayName("测试空标签列表")
    void shouldHandleEmptyTagsList() {
        // 测试默认标签列表
        assertNotNull(note.getTags());
        assertTrue(note.getTags().isEmpty());
        
        // 测试设置空标签列表
        note.setTags(new ArrayList<>());
        assertNotNull(note.getTags());
        assertTrue(note.getTags().isEmpty());
    }
}
