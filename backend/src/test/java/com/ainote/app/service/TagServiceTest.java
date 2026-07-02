package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
@DisplayName("TagService 单元测试")
class TagServiceTest {
    private TagRepository tagRepository;
    private NoteRepository noteRepository;
    private KnowledgeGraphService knowledgeGraphService;
    private SecurityUtils securityUtils;
    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagRepository = mock(TagRepository.class);
        noteRepository = mock(NoteRepository.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        securityUtils = mock(SecurityUtils.class);
        tagService = new TagService(tagRepository, noteRepository, knowledgeGraphService, securityUtils);
    }

    @Test
    @DisplayName("create 应创建新标签")
    void shouldCreateTag() {
        Tag savedTag = new Tag();
        savedTag.setId("tag-123");
        savedTag.setName("Work");
        savedTag.setUserId("user-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findByNameAndUserId("Work", "user-1")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(savedTag);

        com.ainote.app.model.Tag result = tagService.create("Work");

        assertThat(result.getId()).isEqualTo("tag-123");
        assertThat(result.getName()).isEqualTo("Work");

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Work");
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("create reuses current user's existing tag by name")
    void shouldReuseExistingTagForCurrentUser() {
        Tag existingTag = new Tag();
        existingTag.setId("tag-123");
        existingTag.setName("Work");
        existingTag.setUserId("user-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findByNameAndUserId("Work", "user-1")).thenReturn(Optional.of(existingTag));

        com.ainote.app.model.Tag result = tagService.create("Work");

        assertThat(result.getId()).isEqualTo("tag-123");
        assertThat(result.getName()).isEqualTo("Work");
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("listAll 应返回所有标签")
    void shouldListAllTags() {
        Tag tag1 = new Tag();
        tag1.setId("tag-1");
        tag1.setName("Work");
        tag1.setUserId("user-1");

        Tag tag2 = new Tag();
        tag2.setId("tag-2");
        tag2.setName("Personal");
        tag2.setUserId("user-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findAllByUserId("user-1")).thenReturn(List.of(tag1, tag2));

        List<com.ainote.app.model.Tag> result = tagService.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Work");
        assertThat(result.get(1).getName()).isEqualTo("Personal");
        verify(tagRepository).findAllByUserId("user-1");
    }

    @Test
    @DisplayName("listAll 无标签时返回空列表")
    void shouldReturnEmptyListWhenNoTags() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findAllByUserId("user-1")).thenReturn(List.of());

        List<com.ainote.app.model.Tag> result = tagService.listAll();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("delete 应删除标签")
    void shouldDeleteTag() {
        Tag tag = new Tag();
        tag.setId("tag-123");
        tag.setName("Work");
        tag.setUserId("user-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findByIdAndUserId("tag-123", "user-1")).thenReturn(Optional.of(tag));

        tagService.delete("tag-123");

        verify(tagRepository).delete(tag);
    }

    @Test
    @DisplayName("delete ignores tags not owned by current user")
    void shouldNotDeleteTagOwnedByAnotherUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-2");
        when(tagRepository.findByIdAndUserId("tag-123", "user-2")).thenReturn(Optional.empty());

        tagService.delete("tag-123");

        verify(tagRepository, never()).delete(any());
        verify(knowledgeGraphService, never()).deleteTag(any());
    }

    @Test
    @DisplayName("assign 应为笔记分配标签")
    void shouldAssignTagsToNote() {
        Note note = new Note();
        note.setId("note-123");
        note.setTags(new HashSet<>());

        Tag tag1 = new Tag();
        tag1.setId("tag-1");
        tag1.setName("Work");

        Tag tag2 = new Tag();
        tag2.setId("tag-2");
        tag2.setName("Important");

        mockCurrentUserNote("note-123", note);
        when(tagRepository.findByIdAndUserId("tag-1", "user-1")).thenReturn(Optional.of(tag1));
        when(tagRepository.findByIdAndUserId("tag-2", "user-1")).thenReturn(Optional.of(tag2));

        tagService.assign("note-123", List.of("tag-1", "tag-2"));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).hasSize(2);
    }

    @Test
    @DisplayName("assign 不应修改当前用户无权访问的笔记")
    void shouldNotAssignTagsToNoteOwnedByAnotherUser() {
        Note otherUserNote = new Note();
        otherUserNote.setId("note-other-user");
        otherUserNote.setTags(new HashSet<>());

        Tag tag = new Tag();
        tag.setId("tag-1");
        tag.setName("Work");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-other-user", "user-1"))
                .thenReturn(Optional.empty());

        tagService.assign("note-other-user", List.of("tag-1"));

        verify(noteRepository, never()).save(any());
        verify(tagRepository, never()).findByIdAndUserId(any(), any());
        assertThat(otherUserNote.getTags()).isEmpty();
    }

    @Test
    @DisplayName("assign 应跳过不存在的标签")
    void shouldSkipNonexistentTags() {
        Note note = new Note();
        note.setId("note-123");
        note.setTags(new HashSet<>());

        Tag tag1 = new Tag();
        tag1.setId("tag-1");
        tag1.setName("Work");

        mockCurrentUserNote("note-123", note);
        when(tagRepository.findByIdAndUserId("tag-1", "user-1")).thenReturn(Optional.of(tag1));
        when(tagRepository.findByIdAndUserId("tag-nonexistent", "user-1")).thenReturn(Optional.empty());

        tagService.assign("note-123", List.of("tag-1", "tag-nonexistent"));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).hasSize(1);
    }

    @Test
    @DisplayName("assign 笔记不存在时不执行操作")
    void shouldNotAssignWhenNoteNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("nonexistent", "user-1"))
                .thenReturn(Optional.empty());

        tagService.assign("nonexistent", List.of("tag-1"));

        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign 应清除旧标签再添加新标签")
    void shouldClearOldTagsBeforeAssigning() {
        Tag oldTag = new Tag();
        oldTag.setId("old-tag");
        oldTag.setName("Old");

        Note note = new Note();
        note.setId("note-123");
        note.setTags(new HashSet<>(Set.of(oldTag)));

        Tag newTag = new Tag();
        newTag.setId("new-tag");
        newTag.setName("New");

        mockCurrentUserNote("note-123", note);
        when(tagRepository.findByIdAndUserId("new-tag", "user-1")).thenReturn(Optional.of(newTag));

        tagService.assign("note-123", List.of("new-tag"));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).hasSize(1);
        assertThat(captor.getValue().getTags().iterator().next().getName()).isEqualTo("New");
    }

    @Test
    @DisplayName("create 应处理空标签名")
    void shouldHandleEmptyTagName() {
        Tag savedTag = new Tag();
        savedTag.setId("tag-123");
        savedTag.setName("");
        savedTag.setUserId("user-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findByNameAndUserId("", "user-1")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(savedTag);

        com.ainote.app.model.Tag result = tagService.create("");

        assertThat(result.getId()).isEqualTo("tag-123");
        assertThat(result.getName()).isEqualTo("");
    }

    @Test
    @DisplayName("create 应处理null标签名")
    void shouldHandleNullTagName() {
        Tag savedTag = new Tag();
        savedTag.setId("tag-123");
        savedTag.setName(null);
        savedTag.setUserId("user-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(tagRepository.findByNameAndUserId(null, "user-1")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(savedTag);

        com.ainote.app.model.Tag result = tagService.create(null);

        assertThat(result.getId()).isEqualTo("tag-123");
        assertThat(result.getName()).isNull();
    }

    @Test
    @DisplayName("assign 应处理空标签ID列表")
    void shouldHandleEmptyTagIdList() {
        Note note = new Note();
        note.setId("note-123");
        note.setTags(new HashSet<>());

        mockCurrentUserNote("note-123", note);

        tagService.assign("note-123", List.of());

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).isEmpty();
    }

    @Test
    @DisplayName("assign 应处理null标签ID列表")
    void shouldHandleNullTagIdList() {
        Note note = new Note();
        note.setId("note-123");
        note.setTags(new HashSet<>());

        mockCurrentUserNote("note-123", note);

        tagService.assign("note-123", null);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).isEmpty();
    }

    private void mockCurrentUserNote(String noteId, Note note) {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, "user-1"))
                .thenReturn(Optional.of(note));
    }
}
