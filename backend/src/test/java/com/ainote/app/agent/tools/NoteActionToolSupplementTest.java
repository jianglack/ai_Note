package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.ClassificationResponse;
import com.ainote.app.model.ClassificationSuggestion;
import com.ainote.app.model.Note;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.model.Tag;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AiService;
import com.ainote.app.service.NoteService;
import com.ainote.app.service.NoteVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteActionToolSupplementTest {

    private NoteService noteService;
    private NoteRepository noteRepository;
    private SecurityUtils securityUtils;
    private AiService aiService;
    private NoteVersionService noteVersionService;
    private NoteActionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        noteService = mock(NoteService.class);
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        aiService = mock(AiService.class);
        noteVersionService = mock(NoteVersionService.class);
        ToolExecutionPipeline pipeline = mock(ToolExecutionPipeline.class);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<String>>getArgument(4).get());

        tool = new NoteActionTool(
                noteService,
                noteRepository,
                securityUtils,
                aiService,
                noteVersionService,
                pipeline,
                new ObjectMapper());
    }

    @Test
    void listCreateAndSearchBranchesReturnUsefulMessages() {
        when(noteRepository.findByUserIdWithTags("user-1")).thenReturn(List.of());
        assertThat(tool.noteAction("listAll", "{}")).contains("没有任何笔记");

        com.ainote.app.entity.Note existing = entity("note-1", "Daily", "same");
        when(noteRepository.findRecentByUserIdAndTitle(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(existing));
        assertThat(tool.noteAction("create", "{\"title\":\"Daily\",\"content\":\"same\"}"))
                .contains("已存在");
        verify(noteService, never()).create(any());

        Note created = note("created", "Daily", "new");
        when(noteRepository.findRecentByUserIdAndTitle(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(existing));
        when(noteService.create(any(NoteRequest.class))).thenReturn(created);
        assertThat(tool.noteAction("create", "```json\n{\"title\":\"Daily\",\"content\":\"new\",\"tags\":[\"work\"]}\n```"))
                .contains("已创建");
        verify(noteService).create(argThat(request ->
                "Daily".equals(request.getTitle())
                        && "new".equals(request.getContent())
                        && request.getTags().contains("work")));

        when(noteService.hybridSearch("missing")).thenReturn(List.of());
        assertThat(tool.noteAction("search", "{\"query\":\"missing\"}")).contains("未找到");

        List<Note> many = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            many.add(note("note-" + i, "Title " + i, "body"));
        }
        when(noteService.hybridSearch("many")).thenReturn(many);
        assertThat(tool.noteAction("search", "{\"query\":\"many\"}")).contains("找到 11").contains("还有 1");
    }

    @Test
    void updateMoveCopyMergeAndVersionsCoverSuccessAndBoundaryCases() {
        Note original = note("note-1", "Original", "body");
        original.setFolderId("folder-1");
        original.setTags(List.of(tag("tag-1", "work")));
        Note updated = note("note-1", "Updated", "new");
        Note moved = note("note-1", "Moved", "body");
        Note copy = note("copy-1", "Original (副本)", "body");
        Note merged = note("merged-1", "Merged", "combined");

        when(noteService.getById("note-1")).thenReturn(Optional.of(original));
        when(noteService.update(anyString(), any(NoteRequest.class))).thenReturn(Optional.of(updated));
        assertThat(tool.noteAction("update", "{\"noteId\":\"note-1\",\"title\":\"Updated\"}"))
                .contains("已更新");

        assertThat(tool.noteAction("update", "{}")).contains("缺少 noteId");
        when(noteService.getById("missing")).thenReturn(Optional.empty());
        assertThat(tool.noteAction("update", "{\"noteId\":\"missing\"}")).contains("未找到");

        when(noteService.moveToFolder("note-1", null)).thenReturn(Optional.of(moved));
        assertThat(tool.noteAction("move", "{\"noteId\":\"note-1\",\"folderId\":null}"))
                .contains("根目录");
        when(noteService.moveToFolder("note-1", "missing-folder")).thenReturn(Optional.empty());
        assertThat(tool.noteAction("move", "{\"noteId\":\"note-1\",\"folderId\":\"missing-folder\"}"))
                .contains("移动失败");

        when(noteService.copy("note-1")).thenReturn(copy);
        assertThat(tool.noteAction("copy", "{\"noteId\":\"note-1\"}")).contains("副本");
        when(noteService.copy("missing")).thenReturn(null);
        assertThat(tool.noteAction("copy", "{\"noteId\":\"missing\"}")).contains("复制失败");

        assertThat(tool.noteAction("merge", "{\"noteIds\":\"note-1\"}")).contains("至少需要2条");
        when(noteService.merge(List.of("note-1", "note-2"), "Merged")).thenReturn(merged);
        assertThat(tool.noteAction("merge", "{\"noteIds\":\"note-1,note-2\",\"title\":\"Merged\"}"))
                .contains("已合并");

        assertThat(tool.noteAction("versions", "{}")).contains("缺少 noteId");
        when(noteVersionService.getVersions("note-1"))
                .thenReturn(List.of(Map.of("createdAt", "2026-06-27 10:00:00", "content", "old content")));
        assertThat(tool.noteAction("versions", "{\"noteId\":\"note-1\"}"))
                .contains("历史版本")
                .contains("old content");
    }

    @Test
    void destructiveActionsReturnPendingBeforeConfirming() {
        Note note = note("note-1", "Danger", "body");
        when(noteService.getById("note-1")).thenReturn(Optional.of(note));

        assertThat(tool.noteAction("delete", "{\"noteId\":\"note-1\"}"))
                .contains("PENDING_ACTION")
                .contains("DELETE_NOTE");
        verify(noteService, never()).delete("note-1");

        assertThat(tool.noteAction("confirmDelete", "{\"noteId\":\"note-1\"}")).contains("回收站");
        verify(noteService).delete("note-1");

        assertThat(tool.noteAction("permanentDelete", "{\"noteId\":\"note-1\"}"))
                .contains("PENDING_ACTION")
                .contains("PERMANENT_DELETE");
        verify(noteService, never()).permanentDelete("note-1");

        assertThat(tool.noteAction("confirmPermanentDelete", "{\"noteId\":\"note-1\"}")).contains("永久删除");
        verify(noteService).permanentDelete("note-1");

        when(noteService.listDeleted()).thenReturn(List.of(note));
        assertThat(tool.noteAction("emptyTrash", "{}")).contains("PENDING_ACTION").contains("EMPTY_TRASH");
        verify(noteService, never()).emptyTrash();

        when(noteService.emptyTrash()).thenReturn(1);
        assertThat(tool.noteAction("confirmEmptyTrash", "{}")).contains("1 条");
        verify(noteService).emptyTrash();
    }

    @Test
    void tagActionsHandleExistingTagsRetriesAndRemoval() {
        Note withTag = note("note-1", "Tagged", "body");
        withTag.setTags(List.of(tag("tag-1", "work")));
        Note withoutTag = note("note-1", "Tagged", "body");

        when(noteService.getById("note-1"))
                .thenReturn(Optional.of(withTag))
                .thenReturn(Optional.of(withTag))
                .thenReturn(Optional.of(withoutTag))
                .thenReturn(Optional.of(withoutTag));

        assertThat(tool.noteAction("addTag", "{\"noteId\":\"note-1\",\"tagName\":\"work\"}"))
                .contains("已有标签");
        assertThat(tool.noteAction("removeTag", "{\"noteId\":\"note-1\",\"tagName\":\"work\"}"))
                .contains("移除标签");
        assertThat(tool.noteAction("removeTag", "{\"noteId\":\"note-1\",\"tagName\":\"work\"}"))
                .contains("没有标签");
        assertThat(tool.noteAction("addTag", "{\"noteId\":\"note-1\",\"tags\":[\"idea\",\"todo\"]}"))
                .contains("添加标签");

        verify(noteService).update(anyString(), argThat(request -> request.getTags().contains("idea")));
    }

    @Test
    void tagActionsReportOptimisticLockExhaustion() {
        Note note = note("note-1", "Tagged", "body");
        when(noteService.getById("note-1")).thenReturn(Optional.of(note));
        when(noteService.update(anyString(), any(NoteRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("Note", "note-1"));

        String result = tool.noteAction("addTag", "{\"noteId\":\"note-1\",\"tagName\":\"work\"}");

        assertThat(result).contains("稍后重试");
    }

    @Test
    void trashClassifySuggestAndUnknownBranchesAreCovered() {
        Note deleted = note("deleted", "Deleted", "body");
        when(noteService.listDeleted()).thenReturn(List.of()).thenReturn(List.of(deleted));
        assertThat(tool.noteAction("listTrash", "{}")).contains("回收站是空");
        assertThat(tool.noteAction("listTrash", "{}")).contains("Deleted");

        ClassificationSuggestion suggestion = new ClassificationSuggestion(
                "note-1", "Note", null, "Work", "reason", true);
        when(aiService.classifyNotes())
                .thenReturn(new ClassificationResponse(List.of(), List.of(), "无分类"))
                .thenReturn(new ClassificationResponse(List.of(suggestion), List.of("Work"), "完成分类"));
        assertThat(tool.noteAction("classify", "{}")).contains("无分类");
        assertThat(tool.noteAction("classify", "{}")).contains("智能分类").contains("完成分类");

        when(aiService.spiritSuggestTags("note-1")).thenReturn("标签建议");
        assertThat(tool.noteAction("suggestTags", "{\"noteId\":\"note-1\"}")).contains("标签建议");
        assertThat(tool.noteAction("suggestTags", "{}")).contains("缺少 noteId");
        assertThat(tool.noteAction("unknown", "{}")).contains("未知操作");
        assertThat(tool.noteAction("restore", "{\"noteId\":\"note-1\"}")).contains("恢复");
        verify(noteService).restore("note-1");
    }

    private static Note note(String id, String title, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setCreatedAt("2026-06-27 10:00:00");
        note.setUpdatedAt("2026-06-27 10:00:00");
        return note;
    }

    private static Tag tag(String id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private static com.ainote.app.entity.Note entity(String id, String title, String content) {
        com.ainote.app.entity.Note note = new com.ainote.app.entity.Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return note;
    }
}
