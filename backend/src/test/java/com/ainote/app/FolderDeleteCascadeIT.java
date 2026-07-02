package com.ainote.app;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Folder delete cascade")
@RequiresDocker
class FolderDeleteCascadeIT {

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_folder_delete");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.neo4j.enabled", () -> "false");
    }

    @Test
    @Transactional
    void deletingFolderSetsChildNoteFolderIdToNull() {
        User user = userRepository.save(user("user-folder"));
        Folder folder = folderRepository.save(folder("folder-1", user));
        Note note = noteRepository.save(note("note-1", user, folder));
        entityManager.flush();
        entityManager.clear();

        folderRepository.deleteById(folder.getId());
        entityManager.flush();
        entityManager.clear();

        Note reloaded = noteRepository.findById(note.getId()).orElseThrow();
        assertThat(reloaded.getFolder()).isNull();
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setEmail(id + "@example.test");
        user.setPasswordHash("hashed-password");
        user.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return user;
    }

    private static Folder folder(String id, User user) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("Folder");
        folder.setUser(user);
        folder.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        folder.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return folder;
    }

    private static Note note(String id, User user, Folder folder) {
        Note note = new Note();
        note.setId(id);
        note.setTitle("Note");
        note.setContent("body");
        note.setUser(user);
        note.setFolder(folder);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return note;
    }

}
