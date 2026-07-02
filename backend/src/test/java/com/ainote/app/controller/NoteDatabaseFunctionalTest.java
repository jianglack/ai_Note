package com.ainote.app.controller;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteDatabase;
import com.ainote.app.entity.NoteDatabaseRow;
import com.ainote.app.model.NoteDatabaseRequest;
import com.ainote.app.model.NoteDatabaseRowRequest;
import com.ainote.app.repository.NoteDatabaseRepository;
import com.ainote.app.repository.NoteDatabaseRowRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteDatabaseFunctionalTest {

    private NoteDatabaseRepository dbRepository;
    private NoteDatabaseRowRepository rowRepository;
    private NoteRepository noteRepository;
    private NoteDatabaseController controller;

    @BeforeEach
    void setUp() {
        dbRepository = mock(NoteDatabaseRepository.class);
        rowRepository = mock(NoteDatabaseRowRepository.class);
        noteRepository = mock(NoteRepository.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        controller = new NoteDatabaseController(dbRepository, rowRepository, noteRepository, securityUtils);
    }

    @Test
    void databaseAndRowsSupportCrud() {
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(new Note()));
        when(dbRepository.save(any(NoteDatabase.class))).thenAnswer(invocation -> {
            NoteDatabase db = invocation.getArgument(0);
            if (db.getId() == null) {
                db.setId("db-1");
            }
            return db;
        });

        NoteDatabaseRequest create = new NoteDatabaseRequest();
        create.setNoteId("note-1");
        NoteDatabase db = controller.create(create).getBody();

        assertThat(db.getName()).isEqualTo("Untitled Database");
        assertThat(db.getColumns()).isEqualTo("[]");
        assertThat(db.getViewConfig()).isEqualTo("{}");

        NoteDatabaseRow row = new NoteDatabaseRow();
        row.setId("row-1");
        row.setDatabaseId("db-1");
        row.setData("{\"status\":\"todo\"}");
        row.setSortOrder(1);
        when(dbRepository.findByIdAndUserId("db-1", "user-1")).thenReturn(Optional.of(db));
        when(rowRepository.findByDatabaseIdOrderBySortOrderAsc("db-1")).thenReturn(List.of(row));

        Map<String, Object> body = controller.get("db-1").getBody();
        assertThat(body).containsEntry("database", db);
        assertThat(body.get("rows")).isEqualTo(List.of(row));

        when(rowRepository.save(any(NoteDatabaseRow.class))).thenAnswer(invocation -> {
            NoteDatabaseRow saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("row-2");
            }
            return saved;
        });
        NoteDatabaseRowRequest addRow = new NoteDatabaseRowRequest();
        addRow.setData("{\"status\":\"doing\"}");
        NoteDatabaseRow added = controller.addRow("db-1", addRow).getBody();
        assertThat(added.getSortOrder()).isZero();

        when(rowRepository.findByIdAndDatabaseId("row-1", "db-1")).thenReturn(Optional.of(row));
        NoteDatabaseRowRequest updateRow = new NoteDatabaseRowRequest();
        updateRow.setSortOrder(5);
        NoteDatabaseRow updated = controller.updateRow("db-1", "row-1", updateRow).getBody();
        assertThat(updated.getSortOrder()).isEqualTo(5);

        controller.deleteRow("db-1", "row-1");
        verify(rowRepository).delete(row);
        controller.delete("db-1");
        verify(dbRepository).delete(db);
    }
}
