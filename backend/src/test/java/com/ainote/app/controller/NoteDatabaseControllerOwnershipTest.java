package com.ainote.app.controller;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteDatabaseControllerOwnershipTest {

    @Mock
    private NoteDatabaseRepository dbRepository;

    @Mock
    private NoteDatabaseRowRepository rowRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private SecurityUtils securityUtils;

    private NoteDatabaseController controller;

    @BeforeEach
    void setUp() {
        controller = new NoteDatabaseController(dbRepository, rowRepository, noteRepository, securityUtils);
    }

    @Test
    void create_rejectsDatabaseForNoteNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-2", "user-1"))
                .thenReturn(Optional.empty());

        NoteDatabaseRequest request = new NoteDatabaseRequest();
        request.setNoteId("note-2");
        request.setName("db");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(NoSuchElementException.class);

        verify(dbRepository, never()).save(any());
    }

    @Test
    void get_usesCurrentUserOwnershipLookup() {
        NoteDatabase database = database("db-1");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(dbRepository.findByIdAndUserId("db-1", "user-1")).thenReturn(Optional.of(database));

        controller.get("db-1");

        verify(dbRepository).findByIdAndUserId("db-1", "user-1");
        verify(dbRepository, never()).findById("db-1");
    }

    @Test
    void getByNote_validatesNoteOwnershipAndFiltersDatabasesByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(new com.ainote.app.entity.Note()));

        controller.getByNote("note-1");

        verify(noteRepository).findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1");
        verify(dbRepository).findByNoteIdAndUserId("note-1", "user-1");
        verify(dbRepository, never()).findByNoteId("note-1");
    }

    @Test
    void addRow_rejectsDatabaseNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(dbRepository.findByIdAndUserId("db-1", "user-1")).thenReturn(Optional.empty());

        NoteDatabaseRowRequest request = new NoteDatabaseRowRequest();
        request.setData("{}");

        assertThatThrownBy(() -> controller.addRow("db-1", request))
                .isInstanceOf(NoSuchElementException.class);

        verify(rowRepository, never()).save(any());
    }

    @Test
    void updateRow_rejectsRowOutsideOwnedDatabase() {
        NoteDatabase database = database("db-1");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(dbRepository.findByIdAndUserId("db-1", "user-1")).thenReturn(Optional.of(database));
        when(rowRepository.findByIdAndDatabaseId("row-2", "db-1")).thenReturn(Optional.empty());

        NoteDatabaseRowRequest request = new NoteDatabaseRowRequest();
        request.setData("{}");

        assertThatThrownBy(() -> controller.updateRow("db-1", "row-2", request))
                .isInstanceOf(NoSuchElementException.class);

        verify(rowRepository, never()).save(any());
        verify(rowRepository, never()).findById("row-2");
    }

    @Test
    void deleteRow_deletesOnlyRowsInsideOwnedDatabase() {
        NoteDatabase database = database("db-1");
        NoteDatabaseRow row = new NoteDatabaseRow();
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(dbRepository.findByIdAndUserId("db-1", "user-1")).thenReturn(Optional.of(database));
        when(rowRepository.findByIdAndDatabaseId("row-1", "db-1")).thenReturn(Optional.of(row));

        controller.deleteRow("db-1", "row-1");

        verify(rowRepository).delete(row);
        verify(rowRepository, never()).deleteById("row-1");
    }

    private NoteDatabase database(String id) {
        NoteDatabase database = new NoteDatabase();
        database.setId(id);
        database.setUserId("user-1");
        database.setNoteId("note-1");
        database.setName("db");
        database.setColumns("[]");
        return database;
    }
}
