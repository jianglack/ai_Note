package com.ainote.app.service;

import com.ainote.app.entity.NoteConcept;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeGraphServiceRebuildTest {

    @Test
    void rebuildRestoresPersistedConceptProjectionFromPostgres() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Driver> driverProvider = mock(ObjectProvider.class);
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        when(driverProvider.getIfAvailable()).thenReturn(driver);
        when(driver.session()).thenReturn(session);
        when(session.executeWrite(any())).thenReturn(null);

        NoteRepository noteRepository = mock(NoteRepository.class);
        FolderRepository folderRepository = mock(FolderRepository.class);
        ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
        NoteConceptRepository conceptRepository = mock(NoteConceptRepository.class);
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1")).thenReturn(List.of());
        when(scheduleRepository.findByUserIdOrderByStartTimeDesc("user-1")).thenReturn(List.of());
        when(conceptRepository.findByUserId("user-1")).thenReturn(List.of(
                concept("note-1", "backup", "topic", 0.91),
                concept("note-1", "recovery", "topic", 0.88)));

        KnowledgeGraphService service = spy(new KnowledgeGraphService(
                driverProvider, noteRepository, folderRepository, scheduleRepository, conceptRepository));
        doNothing().when(service).syncNoteConcepts(eq("note-1"), any());

        service.rebuildUserGraph("user-1");

        verify(conceptRepository).findByUserId("user-1");
        verify(service).syncNoteConcepts("note-1", List.of(
                Map.of("concept", "backup", "category", "topic", "confidence", 0.91),
                Map.of("concept", "recovery", "category", "topic", "confidence", 0.88)));
    }

    private static NoteConcept concept(String noteId, String value, String category, double confidence) {
        NoteConcept concept = new NoteConcept();
        concept.setNoteId(noteId);
        concept.setUserId("user-1");
        concept.setConcept(value);
        concept.setCategory(category);
        concept.setConfidence(confidence);
        return concept;
    }
}
