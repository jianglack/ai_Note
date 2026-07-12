package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.entity.User;
import com.ainote.app.model.graph.GraphLink;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeGraphServiceWikiLinkTest {

    private NoteRepository noteRepository;
    private FolderRepository folderRepository;
    private ScheduleRepository scheduleRepository;
    private NoteConceptRepository noteConceptRepository;
    private KnowledgeGraphService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<org.neo4j.driver.Driver> driverProvider = mock(ObjectProvider.class);
        noteRepository = mock(NoteRepository.class);
        folderRepository = mock(FolderRepository.class);
        scheduleRepository = mock(ScheduleRepository.class);
        noteConceptRepository = mock(NoteConceptRepository.class);
        service = new KnowledgeGraphService(
                driverProvider, noteRepository, folderRepository, scheduleRepository, noteConceptRepository);
    }

    @Test
    void postgresGraphFallbackBuildsWikiLinkEdgesAndSkipsBrokenLinks() {
        Note source = note("source", "Source", "See [[Target]] and [[Missing]]");
        Note target = note("target", "Target", "body");
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1")).thenReturn(List.of(source, target));
        when(scheduleRepository.findByUserIdOrderByStartTimeDesc("user-1")).thenReturn(List.of());

        var graph = service.getGraph("user-1");

        assertThat(graph.nodes()).extracting(node -> node.id())
                .containsExactlyInAnyOrder("note:source", "note:target");
        assertThat(graph.links()).extracting(GraphLink::type).containsExactly("LINKS_TO");
        assertThat(graph.links().get(0).source()).isEqualTo("note:source");
        assertThat(graph.links().get(0).target()).isEqualTo("note:target");
    }

    @Test
    void postgresGraphFallbackReflectsWikiLinkRenameAndBrokenLinkState() {
        Note source = note("source", "Source", "See [[Target]]");
        Note target = note("target", "Target", "body");
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1")).thenReturn(List.of(source, target));
        when(scheduleRepository.findByUserIdOrderByStartTimeDesc("user-1")).thenReturn(List.of());

        assertThat(service.getGraph("user-1").links())
                .extracting(GraphLink::target)
                .containsExactly("note:target");

        target.setTitle("Renamed Target");
        assertThat(service.getGraph("user-1").links()).isEmpty();

        source.setContent("See [[Renamed Target]]");
        assertThat(service.getGraph("user-1").links())
                .extracting(GraphLink::source, GraphLink::target, GraphLink::type)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "note:source", "note:target", "LINKS_TO"));
    }

    @Test
    void relatedNotesFallbackUsesSeedsWikiLinksFolderAndTagsWithinCurrentUser() {
        Folder folder = new Folder();
        folder.setId("folder-1");
        Tag tag = new Tag("tag-1", "work", "user-1");

        Note seed = note("seed", "Seed", "[[Linked]]");
        seed.setFolder(folder);
        seed.getTags().add(tag);
        Note linked = note("linked", "Linked", "body");
        Note sameFolder = note("folder-note", "Folder note", "body");
        sameFolder.setFolder(folder);
        Note sameTag = note("tag-note", "Tag note", "body");
        sameTag.getTags().add(tag);

        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1"))
                .thenReturn(List.of(seed, linked, sameFolder, sameTag));
        when(noteRepository.findAllById(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0, List.class).stream()
                        .map(id -> switch ((String) id) {
                            case "seed" -> seed;
                            case "linked" -> linked;
                            case "folder-note" -> sameFolder;
                            case "tag-note" -> sameTag;
                            default -> null;
                        })
                        .filter(java.util.Objects::nonNull)
                        .toList());

        List<Note> result = service.searchRelatedNotes("user-1", "", List.of("seed"), 4);

        assertThat(result).extracting(Note::getId)
                .containsExactly("seed", "linked", "folder-note", "tag-note");
    }

    private static Note note(String id, String title, String content) {
        User user = new User();
        user.setId("user-1");
        user.setUsername("alice");

        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setUser(user);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return note;
    }
}
