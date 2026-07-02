package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteVersion;
import com.ainote.app.repository.NoteVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
@DisplayName("NoteVersionService 单元测试")
class NoteVersionServiceTest {
    private NoteVersionRepository noteVersionRepository;
    private NoteVersionService noteVersionService;

    @BeforeEach
    void setUp() {
        noteVersionRepository = mock(NoteVersionRepository.class);
        noteVersionService = new NoteVersionService(noteVersionRepository);
    }

    @Test
    @DisplayName("getVersions 应返回笔记版本列表")
    void shouldGetVersions() {
        Note note = new Note();
        note.setId("note-123");

        NoteVersion version1 = new NoteVersion();
        version1.setId("version-1");
        version1.setNote(note);
        version1.setContent("<p>Version 1 content</p>");
        version1.setCreatedAt(LocalDateTime.of(2026, 4, 8, 10, 0, 0));

        NoteVersion version2 = new NoteVersion();
        version2.setId("version-2");
        version2.setNote(note);
        version2.setContent("<p>Version 2 content</p>");
        version2.setCreatedAt(LocalDateTime.of(2026, 4, 8, 11, 0, 0));

        when(noteVersionRepository.findByNoteIdOrderByCreatedAtDesc("note-123"))
            .thenReturn(List.of(version2, version1));

        List<Map<String, Object>> result = noteVersionService.getVersions("note-123");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("id")).isEqualTo("version-2");
        assertThat(result.get(0).get("noteId")).isEqualTo("note-123");
        assertThat(result.get(0).get("content")).isEqualTo("<p>Version 2 content</p>");
        assertThat(result.get(0).get("createdAt")).isEqualTo("2026-04-08 11:00:00");
    }

    @Test
    @DisplayName("getVersions 无版本时返回空列表")
    void shouldReturnEmptyListWhenNoVersions() {
        when(noteVersionRepository.findByNoteIdOrderByCreatedAtDesc("note-123"))
            .thenReturn(List.of());

        List<Map<String, Object>> result = noteVersionService.getVersions("note-123");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getVersions 应按时间倒序排列")
    void shouldReturnVersionsInDescendingOrder() {
        Note note = new Note();
        note.setId("note-123");

        NoteVersion older = new NoteVersion();
        older.setId("older");
        older.setNote(note);
        older.setContent("Old content");
        older.setCreatedAt(LocalDateTime.of(2026, 4, 1, 10, 0, 0));

        NoteVersion newer = new NoteVersion();
        newer.setId("newer");
        newer.setNote(note);
        newer.setContent("New content");
        newer.setCreatedAt(LocalDateTime.of(2026, 4, 8, 10, 0, 0));

        // Repository 已按 DESC 排序返回
        when(noteVersionRepository.findByNoteIdOrderByCreatedAtDesc("note-123"))
            .thenReturn(List.of(newer, older));

        List<Map<String, Object>> result = noteVersionService.getVersions("note-123");

        assertThat(result.get(0).get("id")).isEqualTo("newer");
        assertThat(result.get(1).get("id")).isEqualTo("older");
    }
}
