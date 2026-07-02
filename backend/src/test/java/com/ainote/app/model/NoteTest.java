package com.ainote.app.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NoteTest {

    private Note note;

    @BeforeEach
    void setUp() {
        note = new Note();
    }

    @Test
    @DisplayName("测试默认构造方法")
    void shouldCreateNoteWithDefaultConstructor() {
        assertThat(note).isNotNull();
        assertThat(note.getId()).isNull();
        assertThat(note.getTitle()).isNull();
        assertThat(note.getContent()).isNull();
        assertThat(note.getCreatedAt()).isNull();
        assertThat(note.getUpdatedAt()).isNull();
        assertThat(note.getDeletedAt()).isNull();
        assertThat(note.getFolderId()).isNull();
        assertThat(note.getTags()).isEmpty();
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
        assertThat(note.getId()).isEqualTo("note-123");
        assertThat(note.getTitle()).isEqualTo("Test Note");
        assertThat(note.getContent()).isEqualTo("This is a test note");
        assertThat(note.getCreatedAt()).isEqualTo(createdAt);
        assertThat(note.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(note.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(note.getFolderId()).isEqualTo("folder-123");
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
        assertThat(note.getTags()).hasSize(2);
        assertThat(note.getTags().get(0).getId()).isEqualTo("tag-1");
        assertThat(note.getTags().get(0).getName()).isEqualTo("Tag 1");
        assertThat(note.getTags().get(1).getId()).isEqualTo("tag-2");
        assertThat(note.getTags().get(1).getName()).isEqualTo("Tag 2");

        // 清空标签
        note.getTags().clear();
        assertThat(note.getTags()).isEmpty();
    }

    @Test
    @DisplayName("测试toString方法")
    void shouldGenerateStringRepresentation() {
        note.setId("note-123");
        note.setTitle("Test Note");
        String toString = note.toString();
        assertThat(toString).isNotNull();
    }

    @Test
    @DisplayName("测试空标签列表")
    void shouldHandleEmptyTagsList() {
        // 测试默认标签列表
        assertThat(note.getTags()).isNotNull();
        assertThat(note.getTags()).isEmpty();
        
        // 测试设置空标签列表
        note.setTags(new ArrayList<>());
        assertThat(note.getTags()).isNotNull();
        assertThat(note.getTags()).isEmpty();
    }
}
