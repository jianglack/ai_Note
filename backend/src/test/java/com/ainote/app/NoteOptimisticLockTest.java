package com.ainote.app;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Note optimistic locking")
@RequiresDocker
class NoteOptimisticLockTest {

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_note_lock");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    void concurrentUpdatesToSameNoteRejectSecondCommit() {
        User user = userRepository.saveAndFlush(user("user-lock"));
        Note note = note("note-lock", "Original", user);
        noteRepository.saveAndFlush(note);

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();

            Note firstCopy = first.find(Note.class, "note-lock");
            Note secondCopy = second.find(Note.class, "note-lock");

            firstCopy.setTitle("First commit");
            secondCopy.setTitle("Second commit");

            first.getTransaction().commit();

            assertThatThrownBy(() -> second.getTransaction().commit())
                    .isInstanceOf(RollbackException.class);
        } finally {
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
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

    private static Note note(String id, String title, User user) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent("body");
        note.setUser(user);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return note;
    }

}
