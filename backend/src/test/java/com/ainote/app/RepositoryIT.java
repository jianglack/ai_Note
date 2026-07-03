package com.ainote.app;

import com.ainote.app.entity.AgentTrace;
import com.ainote.app.entity.EpisodicMemory;
import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.entity.Notification;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.TaskSchedule;
import com.ainote.app.entity.User;
import com.ainote.app.controller.NotificationController;
import com.ainote.app.model.NotificationResponse;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.TaskScheduleRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import com.ainote.app.repository.UserRepository;
import com.ainote.app.service.ProactiveScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Repository custom queries")
@RequiresDocker
class RepositoryIT {

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_repository");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private AgentTraceRepository agentTraceRepository;

    @Autowired
    private TaskScheduleRepository taskScheduleRepository;

    @Autowired
    private NoteConceptRepository noteConceptRepository;

    @Autowired
    private SemanticMemoryRepository semanticMemoryRepository;

    @Autowired
    private MemoryEventRepository memoryEventRepository;

    @Autowired
    private EpisodicMemoryRepository episodicMemoryRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProactiveScheduler proactiveScheduler;

    @Autowired
    private NotificationController notificationController;

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
    void noteAndFolderQueriesAreScopedByUserAndRespectPagination() {
        User user = userRepository.save(user("user-repo"));
        User other = userRepository.save(user("other-repo"));
        Folder folder = folderRepository.save(folder("folder-1", user));
        folderRepository.save(folder("folder-2", other));
        Note newest = noteRepository.save(note("note-2", "Beta", user, folder, "2026-06-27T11:00:00"));
        noteRepository.save(note("note-1", "Alpha", user, folder, "2026-06-27T10:00:00"));
        noteRepository.save(note("other-note", "Other", other, null, "2026-06-27T12:00:00"));

        assertThat(folderRepository.findByUserIdAndName("user-repo", "Folder"))
                .extracting(Folder::getId)
                .containsExactly("folder-1");
        assertThat(noteRepository.findNoteIdsByUserId("user-repo", PageRequest.of(0, 1)).getContent())
                .containsExactly(newest.getId());
        assertThat(noteRepository.searchByUserIdAndQuery("user-repo", "Beta"))
                .extracting(Note::getId)
                .containsExactly("note-2");
        assertThat(noteRepository.countByUserIdAndDeletedAtIsNull("user-repo")).isEqualTo(2);
    }

    @Test
    void agentTraceAndTaskScheduleQueriesAggregateCurrentData() {
        AgentTrace trace = new AgentTrace();
        trace.setId("trace-1");
        trace.setTraceId("request-1");
        trace.setUserId("user-repo");
        trace.setModel("deepseek-chat");
        trace.setTotalTokens(123);
        trace.setToolsCalled("[{\"name\":\"noteAction\"}]");
        trace.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        agentTraceRepository.save(trace);

        TaskSchedule due = new TaskSchedule();
        due.setUserId("user-repo");
        due.setOriginalQuery("run");
        due.setPlanTemplateJson("{}");
        due.setTriggerType("ONCE");
        due.setEnabled(true);
        due.setNextRunAt(LocalDateTime.parse("2026-06-27T09:00:00"));
        taskScheduleRepository.save(due);

        assertThat(agentTraceRepository.sumTotalTokensByUserId("user-repo")).isEqualTo(123);
        assertThat(agentTraceRepository.findFilteredTraces(
                "user-repo",
                LocalDateTime.parse("2026-06-27T00:00:00"),
                LocalDateTime.parse("2026-06-28T00:00:00"),
                "deepseek-chat",
                PageRequest.of(0, 10))).extracting(AgentTrace::getId).containsExactly("trace-1");
        assertThat(taskScheduleRepository.findDueSchedules(LocalDateTime.parse("2026-06-27T10:00:00")))
                .extracting(TaskSchedule::getOriginalQuery)
                .containsExactly("run");
    }

    @Test
    @Transactional
    void conceptAndMemoryRepositoriesPersistAndQueryUserScopedRows() {
        User user = userRepository.save(user("memory-user"));
        noteRepository.save(note("note-memory-1", "Memory note", user, null, "2026-06-27T12:00:00"));

        NoteConcept concept = new NoteConcept();
        concept.setUserId("memory-user");
        concept.setNoteId("note-memory-1");
        concept.setConcept("planning");
        concept.setCategory("topic");
        concept.setConfidence(0.91);
        noteConceptRepository.save(concept);

        SemanticMemory semantic = new SemanticMemory();
        semantic.setUserId("memory-user");
        semantic.setCategory("preference");
        semantic.setMemoryType("preference");
        semantic.setScope("user");
        semantic.setStatus("active");
        semantic.setContent("prefers concise release notes");
        semantic.setConfidence(0.88);
        semantic.setDecayScore(0.93);
        semanticMemoryRepository.save(semantic);
        semanticMemoryRepository.updateEmbedding(semantic.getId(), semanticVector(1.0f));

        SemanticMemory deleted = new SemanticMemory();
        deleted.setUserId("memory-user");
        deleted.setCategory("preference");
        deleted.setMemoryType("preference");
        deleted.setScope("user");
        deleted.setStatus("deleted");
        deleted.setContent("deleted memory");
        deleted.setConfidence(0.8);
        deleted.setDecayScore(0.99);
        semanticMemoryRepository.save(deleted);

        MemoryEvent event = new MemoryEvent();
        event.setUserId("memory-user");
        event.setMemoryId(semantic.getId());
        event.setEventType("CREATED");
        event.setActor("system");
        event.setReason("test");
        memoryEventRepository.save(event);

        EpisodicMemory episodic = new EpisodicMemory();
        episodic.setUserId("memory-user");
        episodic.setSessionSummary("Discussed the launch checklist");
        episodic.setKeyTopics("[\"launch\"]");
        episodic.setActionsTaken("[\"created checklist\"]");
        episodic.setMessageCount(3);
        episodicMemoryRepository.save(episodic);

        assertThat(noteConceptRepository.findDistinctConceptsByUserId("memory-user"))
                .containsExactly("planning");
        assertThat(noteConceptRepository.findTopConceptsByUserId("memory-user", PageRequest.of(0, 5)))
                .containsExactly("planning");
        assertThat(noteConceptRepository.findNoteIdsByUserIdAndConcept("memory-user", "planning"))
                .containsExactly("note-memory-1");
        assertThat(noteConceptRepository.findByUserIdAndConceptIn("memory-user", List.of("planning")))
                .extracting(NoteConcept::getNoteId)
                .containsExactly("note-memory-1");

        assertThat(semanticMemoryRepository.countByUserId("memory-user")).isEqualTo(2);
        assertThat(semanticMemoryRepository.findTopByUserId("memory-user", PageRequest.of(0, 5)))
                .extracting(SemanticMemory::getContent)
                .containsExactly("prefers concise release notes");
        assertThat(semanticMemoryRepository.findByUserIdAndCategory("memory-user", "preference"))
                .extracting(SemanticMemory::getContent)
                .containsExactly("prefers concise release notes");
        assertThat(semanticMemoryRepository.findByUserIdAndContent("memory-user", "prefers concise release notes"))
                .extracting(SemanticMemory::getCategory)
                .containsExactly("preference");
        assertThat(semanticMemoryRepository.findLowestScored("memory-user", 10))
                .extracting(SemanticMemory::getId)
                .containsExactly(semantic.getId());
        assertThat(semanticMemoryRepository.findByUserIdWithEmbedding("memory-user"))
                .extracting(SemanticMemory::getContent)
                .containsExactly("prefers concise release notes");
        assertThat(semanticMemoryRepository.findSimilarByEmbedding("memory-user", semanticVector(1.0f), 0.99, 5))
                .extracting(SemanticMemory::getContent)
                .containsExactly("prefers concise release notes");
        assertThat(memoryEventRepository.findByUserIdOrderByCreatedAtDesc("memory-user"))
                .extracting(MemoryEvent::getEventType)
                .containsExactly("CREATED");

        assertThat(episodicMemoryRepository.countByUserId("memory-user")).isEqualTo(1);
        assertThat(episodicMemoryRepository.findRecentByUserId("memory-user", PageRequest.of(0, 5)))
                .extracting(EpisodicMemory::getSessionSummary)
                .containsExactly("Discussed the launch checklist");

        semanticMemoryRepository.deleteByIds(List.of(semantic.getId(), deleted.getId()));
        assertThat(semanticMemoryRepository.countByUserId("memory-user")).isZero();
    }

    @Test
    @Transactional
    void scheduleReminderNotificationFlowsThroughControllerAndReadState() {
        User user = userRepository.save(user("notification-user"));
        scheduleRepository.save(schedule("schedule-notification", "Launch review", user,
                LocalDateTime.now().minusMinutes(5)));

        proactiveScheduler.checkScheduleReminders();
        proactiveScheduler.checkScheduleReminders();

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("notification-user"))
                .hasSize(1)
                .first()
                .extracting(Notification::getType, Notification::getRelatedId, Notification::getIsRead)
                .containsExactly("overdue", "schedule-notification", false);

        authenticateAs("notification-user");
        try {
            List<NotificationResponse> unread = notificationController.getUnread();
            assertThat(unread)
                    .extracting(NotificationResponse::title)
                    .containsExactly("Schedule overdue: Launch review");
            assertThat(notificationController.getUnreadCount()).containsEntry("count", 1L);

            notificationController.markAsRead(unread.get(0).id());

            assertThat(notificationController.getUnread()).isEmpty();
            assertThat(notificationController.getUnreadCount()).containsEntry("count", 0L);
        } finally {
            SecurityContextHolder.clearContext();
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

    private static Folder folder(String id, User user) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("Folder");
        folder.setUser(user);
        folder.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        folder.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return folder;
    }

    private static Note note(String id, String title, User user, Folder folder, String updatedAt) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent("body");
        note.setUser(user);
        note.setFolder(folder);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse(updatedAt));
        return note;
    }

    private static com.ainote.app.entity.Schedule schedule(
            String id,
            String title,
            User user,
            LocalDateTime startTime) {
        com.ainote.app.entity.Schedule schedule = new com.ainote.app.entity.Schedule();
        schedule.setId(id);
        schedule.setTitle(title);
        schedule.setUser(user);
        schedule.setStartTime(startTime);
        schedule.setEndTime(startTime.plusMinutes(30));
        schedule.setStatus("pending");
        schedule.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        schedule.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return schedule;
    }

    private static void authenticateAs(String username) {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password("unused")
                .authorities("ROLE_USER")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "unused", principal.getAuthorities()));
    }

    private static String semanticVector(float firstValue) {
        StringBuilder vector = new StringBuilder("[");
        for (int i = 0; i < 1024; i++) {
            if (i > 0) {
                vector.append(",");
            }
            vector.append(i == 0 ? firstValue : 0.0f);
        }
        return vector.append("]").toString();
    }

}
