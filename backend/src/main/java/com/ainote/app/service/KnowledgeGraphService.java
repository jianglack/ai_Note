package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Schedule;
import com.ainote.app.entity.Tag;
import com.ainote.app.model.graph.GraphLink;
import com.ainote.app.model.graph.GraphNode;
import com.ainote.app.model.graph.KnowledgeGraphResponse;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private final Driver driver;
    private final NoteRepository noteRepository;
    private final FolderRepository folderRepository;
    private final ScheduleRepository scheduleRepository;

    public KnowledgeGraphService(
            ObjectProvider<Driver> driverProvider,
            NoteRepository noteRepository,
            FolderRepository folderRepository,
            ScheduleRepository scheduleRepository) {
        this.driver = driverProvider.getIfAvailable();
        this.noteRepository = noteRepository;
        this.folderRepository = folderRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @PostConstruct
    public void ensureConstraints() {
        if (!isNeo4jEnabled()) {
            return;
        }
        runWrite(tx -> {
            tx.run("CREATE CONSTRAINT ainote_user_id IF NOT EXISTS FOR (n:User) REQUIRE n.id IS UNIQUE");
            tx.run("CREATE CONSTRAINT ainote_note_id IF NOT EXISTS FOR (n:Note) REQUIRE n.id IS UNIQUE");
            tx.run("CREATE CONSTRAINT ainote_folder_id IF NOT EXISTS FOR (n:Folder) REQUIRE n.id IS UNIQUE");
            tx.run("CREATE CONSTRAINT ainote_tag_id IF NOT EXISTS FOR (n:Tag) REQUIRE n.id IS UNIQUE");
            tx.run("CREATE CONSTRAINT ainote_schedule_id IF NOT EXISTS FOR (n:Schedule) REQUIRE n.id IS UNIQUE");
            tx.run("CREATE FULLTEXT INDEX ainote_note_text IF NOT EXISTS FOR (n:Note) ON EACH [n.title, n.contentPreview]");
            tx.run("CREATE FULLTEXT INDEX ainote_tag_text IF NOT EXISTS FOR (n:Tag) ON EACH [n.name]");
            tx.run("CREATE FULLTEXT INDEX ainote_folder_text IF NOT EXISTS FOR (n:Folder) ON EACH [n.name]");
            return null;
        });
    }

    public boolean isNeo4jEnabled() {
        return driver != null;
    }

    @Transactional(readOnly = true)
    public List<Note> searchRelatedNotes(String userId, String query, List<String> seedNoteIds, int limit) {
        List<String> safeSeedIds = seedNoteIds == null ? List.of() : seedNoteIds;
        List<String> resultIds = isNeo4jEnabled()
                ? searchRelatedNoteIdsFromNeo4j(userId, query, safeSeedIds, limit)
                : searchRelatedNoteIdsFromPostgres(userId, safeSeedIds, limit);

        Map<String, Note> notesById = noteRepository.findAllById(resultIds).stream()
                .filter(note -> note.getDeletedAt() == null)
                .filter(note -> note.getUser() != null && userId.equals(note.getUser().getId()))
                .collect(Collectors.toMap(Note::getId, note -> note));

        List<Note> ordered = new ArrayList<>();
        for (String id : resultIds) {
            Note note = notesById.get(id);
            if (note != null) {
                ordered.add(note);
            }
        }
        return ordered;
    }

    @Transactional(readOnly = true)
    public KnowledgeGraphResponse getGraph(String userId) {
        if (!isNeo4jEnabled()) {
            return buildGraphFromPostgres(userId, false);
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, GraphNode> nodes = new LinkedHashMap<>();
                List<GraphLink> links = new ArrayList<>();

                tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(source)
                        WHERE NOT source:Note OR coalesce(source.deleted, false) = false
                        OPTIONAL MATCH (source)-[r:HAS_TAG|IN_FOLDER|LINKS_TO|PARENT_OF]->(target)
                        WHERE target IS NULL OR NOT target:Note OR coalesce(target.deleted, false) = false
                        WITH source, target
                        UNWIND [source, target] AS node
                        WITH DISTINCT node
                        WHERE node IS NOT NULL
                        RETURN node
                        """, Map.of("userId", userId)).forEachRemaining(record -> {
                    GraphNode node = toGraphNode(record.get("node").asNode());
                    nodes.put(node.id(), node);
                });

                tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(source)-[r:HAS_TAG|IN_FOLDER|LINKS_TO|PARENT_OF]->(target)
                        WHERE (NOT source:Note OR coalesce(source.deleted, false) = false)
                          AND (NOT target:Note OR coalesce(target.deleted, false) = false)
                        RETURN source.id AS sourceId, labels(source) AS sourceLabels,
                               target.id AS targetId, labels(target) AS targetLabels,
                               type(r) AS type
                        """, Map.of("userId", userId)).forEachRemaining(record -> links.add(new GraphLink(
                        graphId(record.get("sourceLabels").asList(value -> value.asString()), record.get("sourceId").asString()),
                        graphId(record.get("targetLabels").asList(value -> value.asString()), record.get("targetId").asString()),
                        record.get("type").asString(),
                        linkLabel(record.get("type").asString())
                )));

                return new KnowledgeGraphResponse(new ArrayList<>(nodes.values()), links, true);
            });
        } catch (Exception e) {
            log.warn("Neo4j graph query failed, using PostgreSQL fallback: {}", e.getMessage());
            return buildGraphFromPostgres(userId, true);
        }
    }

    @Transactional
    public void syncNote(String noteId) {
        if (!isNeo4jEnabled()) {
            return;
        }
        Optional<Note> noteOpt = noteRepository.findById(noteId);
        if (noteOpt.isEmpty()) {
            deleteNote(noteId);
            return;
        }

        Note note = noteOpt.get();
        runWrite(tx -> {
            upsertUser(tx, note.getUser().getId(), note.getUser().getUsername());
            upsertNote(tx, note);
            tx.run("MATCH (:User {id: $userId})-[r:OWNS]->(:Note {id: $noteId}) DELETE r",
                    Map.of("userId", note.getUser().getId(), "noteId", note.getId()));
            tx.run("""
                    MATCH (u:User {id: $userId}), (n:Note {id: $noteId})
                    MERGE (u)-[:OWNS]->(n)
                    """, Map.of("userId", note.getUser().getId(), "noteId", note.getId()));

            tx.run("MATCH (:Note {id: $noteId})-[r:HAS_TAG|IN_FOLDER|LINKS_TO]->() DELETE r",
                    Map.of("noteId", note.getId()));

            if (note.getFolder() != null) {
                upsertFolder(tx, note.getFolder());
                tx.run("""
                        MATCH (n:Note {id: $noteId}), (f:Folder {id: $folderId})
                        MERGE (n)-[:IN_FOLDER]->(f)
                        """, Map.of("noteId", note.getId(), "folderId", note.getFolder().getId()));
            }

            for (Tag tag : note.getTags()) {
                upsertTag(tx, tag);
                tx.run("""
                        MATCH (n:Note {id: $noteId}), (t:Tag {id: $tagId})
                        MERGE (n)-[:HAS_TAG]->(t)
                        """, Map.of("noteId", note.getId(), "tagId", tag.getId()));
            }

            for (Note target : resolveWikiTargets(note)) {
                upsertNote(tx, target);
                tx.run("""
                        MATCH (n:Note {id: $noteId}), (target:Note {id: $targetId})
                        MERGE (n)-[:LINKS_TO]->(target)
                        """, Map.of("noteId", note.getId(), "targetId", target.getId()));
            }
            return null;
        });
    }

    @Transactional
    public void syncFolder(String folderId) {
        if (!isNeo4jEnabled()) {
            return;
        }
        folderRepository.findById(folderId).ifPresent(folder -> runWrite(tx -> {
            upsertUser(tx, folder.getUser().getId(), folder.getUser().getUsername());
            upsertFolder(tx, folder);
            tx.run("MATCH (:User {id: $userId})-[r:OWNS]->(:Folder {id: $folderId}) DELETE r",
                    Map.of("userId", folder.getUser().getId(), "folderId", folder.getId()));
            tx.run("""
                    MATCH (u:User {id: $userId}), (f:Folder {id: $folderId})
                    MERGE (u)-[:OWNS]->(f)
                    """, Map.of("userId", folder.getUser().getId(), "folderId", folder.getId()));
            tx.run("MATCH (:Folder {id: $folderId})-[r:PARENT_OF]->() DELETE r",
                    Map.of("folderId", folder.getId()));
            if (folder.getParentId() != null) {
                tx.run("""
                        MATCH (parent:Folder {id: $parentId}), (child:Folder {id: $folderId})
                        MERGE (parent)-[:PARENT_OF]->(child)
                        """, Map.of("parentId", folder.getParentId(), "folderId", folder.getId()));
            }
            return null;
        }));
    }

    @Transactional
    public void syncSchedule(String scheduleId) {
        if (!isNeo4jEnabled()) {
            return;
        }
        scheduleRepository.findById(scheduleId).ifPresent(schedule -> runWrite(tx -> {
            upsertUser(tx, schedule.getUser().getId(), schedule.getUser().getUsername());
            upsertSchedule(tx, schedule);
            tx.run("MATCH (:User {id: $userId})-[r:OWNS]->(:Schedule {id: $scheduleId}) DELETE r",
                    Map.of("userId", schedule.getUser().getId(), "scheduleId", schedule.getId()));
            tx.run("""
                    MATCH (u:User {id: $userId}), (s:Schedule {id: $scheduleId})
                    MERGE (u)-[:OWNS]->(s)
                    """, Map.of("userId", schedule.getUser().getId(), "scheduleId", schedule.getId()));
            return null;
        }));
    }

    public void deleteNote(String noteId) {
        deleteNode("Note", noteId);
    }

    public void deleteFolder(String folderId) {
        deleteNode("Folder", folderId);
    }

    public void deleteTag(String tagId) {
        deleteNode("Tag", tagId);
    }

    public void deleteSchedule(String scheduleId) {
        deleteNode("Schedule", scheduleId);
    }

    @Transactional(readOnly = true)
    public void rebuildUserGraph(String userId) {
        if (!isNeo4jEnabled()) {
            return;
        }
        runWrite(tx -> {
            tx.run("""
                    MATCH (u:User {id: $userId})-[:OWNS]->(n)
                    DETACH DELETE n
                    """, Map.of("userId", userId));
            return null;
        });
        folderRepository.findByUserId(userId).forEach(folder -> syncFolder(folder.getId()));
        noteRepository.findByUserIdAndDeletedAtIsNull(userId).forEach(note -> syncNote(note.getId()));
        scheduleRepository.findByUserIdOrderByStartTimeDesc(userId).forEach(schedule -> syncSchedule(schedule.getId()));
    }

    private List<String> searchRelatedNoteIdsFromNeo4j(String userId, String query, List<String> seedNoteIds, int limit) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("userId", userId);
                params.put("query", sanitizeFullTextQuery(query));
                params.put("seedIds", seedNoteIds);
                params.put("limit", limit);

                List<String> ids = new ArrayList<>();
                tx.run("""
                        MATCH (:User {id: $userId})-[:OWNS]->(seed:Note)
                        WHERE seed.id IN $seedIds AND coalesce(seed.deleted, false) = false
                        WITH collect(seed) AS vectorSeeds
                        CALL {
                          WITH vectorSeeds
                          UNWIND vectorSeeds AS seed
                          OPTIONAL MATCH (seed)-[:LINKS_TO|HAS_TAG|IN_FOLDER]-(related:Note)
                          WHERE coalesce(related.deleted, false) = false
                          RETURN collect(seed) + collect(related) AS vectorMatches
                        }
                        CALL {
                          WITH vectorSeeds
                          CALL db.index.fulltext.queryNodes("ainote_note_text", $query, {limit: $limit}) YIELD node, score
                          MATCH (:User {id: $userId})-[:OWNS]->(node)
                          WHERE coalesce(node.deleted, false) = false
                          OPTIONAL MATCH (node)-[:LINKS_TO|HAS_TAG|IN_FOLDER]-(neighbor:Note)
                          WHERE coalesce(neighbor.deleted, false) = false
                          RETURN collect(node) + collect(neighbor) AS noteMatches
                        }
                        CALL {
                          CALL db.index.fulltext.queryNodes("ainote_tag_text", $query, {limit: $limit}) YIELD node, score
                          MATCH (:User {id: $userId})-[:OWNS]->(:Note)-[:HAS_TAG]->(node)
                          MATCH (note:Note)-[:HAS_TAG]->(node)
                          MATCH (:User {id: $userId})-[:OWNS]->(note)
                          WHERE coalesce(note.deleted, false) = false
                          RETURN collect(note) AS tagMatches
                        }
                        CALL {
                          CALL db.index.fulltext.queryNodes("ainote_folder_text", $query, {limit: $limit}) YIELD node, score
                          MATCH (:User {id: $userId})-[:OWNS]->(node)
                          MATCH (note:Note)-[:IN_FOLDER]->(node)
                          MATCH (:User {id: $userId})-[:OWNS]->(note)
                          WHERE coalesce(note.deleted, false) = false
                          RETURN collect(note) AS folderMatches
                        }
                        WITH vectorSeeds + vectorMatches + noteMatches + tagMatches + folderMatches AS allMatches
                        UNWIND allMatches AS note
                        WITH DISTINCT note
                        WHERE note IS NOT NULL
                        RETURN note.id AS id
                        LIMIT $limit
                        """, params).forEachRemaining(record -> ids.add(record.get("id").asString()));
                return ids;
            });
        } catch (Exception e) {
            log.warn("Neo4j graph search failed, using PostgreSQL graph fallback: {}", e.getMessage());
            return searchRelatedNoteIdsFromPostgres(userId, seedNoteIds, limit);
        }
    }

    private List<String> searchRelatedNoteIdsFromPostgres(String userId, List<String> seedNoteIds, int limit) {
        List<Note> userNotes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
        Map<String, Note> byId = userNotes.stream().collect(Collectors.toMap(Note::getId, note -> note, (a, b) -> a));
        Map<String, Note> byTitle = new HashMap<>();
        for (Note note : userNotes) {
            byTitle.putIfAbsent(note.getTitle(), note);
        }
        Map<String, List<Note>> byFolderId = userNotes.stream()
                .filter(note -> note.getFolder() != null)
                .collect(Collectors.groupingBy(note -> note.getFolder().getId()));
        Map<String, List<Note>> byTagId = new HashMap<>();
        for (Note note : userNotes) {
            for (Tag tag : note.getTags()) {
                byTagId.computeIfAbsent(tag.getId(), ignored -> new ArrayList<>()).add(note);
            }
        }

        LinkedHashMap<String, Note> result = new LinkedHashMap<>();
        for (String seedId : seedNoteIds) {
            Note seed = byId.get(seedId);
            if (seed == null) {
                continue;
            }
            result.put(seed.getId(), seed);
            for (String title : extractWikiLinks(seed.getContent())) {
                Note linked = byTitle.get(title);
                if (linked != null) {
                    result.put(linked.getId(), linked);
                }
            }
            if (seed.getFolder() != null) {
                for (Note candidate : byFolderId.getOrDefault(seed.getFolder().getId(), List.of())) {
                    result.put(candidate.getId(), candidate);
                }
            }
            Set<String> tagIds = seed.getTags().stream().map(Tag::getId).collect(Collectors.toSet());
            if (!tagIds.isEmpty()) {
                for (String tagId : tagIds) {
                    for (Note candidate : byTagId.getOrDefault(tagId, List.of())) {
                        result.put(candidate.getId(), candidate);
                    }
                }
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result.keySet().stream().limit(limit).collect(Collectors.toList());
    }

    private String sanitizeFullTextQuery(String query) {
        if (query == null || query.isBlank()) {
            return "*";
        }
        String sanitized = query.replaceAll("[+\\-!(){}\\[\\]^\"~*?:\\\\/]|&&|\\|\\|", " ").trim();
        return sanitized.isBlank() ? "*" : sanitized;
    }

    private KnowledgeGraphResponse buildGraphFromPostgres(String userId, boolean neo4jEnabled) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();

        List<Folder> folders = folderRepository.findByUserId(userId);
        for (Folder folder : folders) {
            nodes.put("folder:" + folder.getId(), new GraphNode("folder:" + folder.getId(), folder.getName(), "folder", folder.getId()));
            if (folder.getParentId() != null) {
                links.add(new GraphLink("folder:" + folder.getParentId(), "folder:" + folder.getId(), "PARENT_OF", "parent"));
            }
        }

        List<Note> notes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
        Map<String, Note> titleToNote = new HashMap<>();
        for (Note note : notes) {
            nodes.put("note:" + note.getId(), new GraphNode("note:" + note.getId(), note.getTitle(), "note", note.getId()));
            titleToNote.putIfAbsent(note.getTitle(), note);

            if (note.getFolder() != null) {
                links.add(new GraphLink("note:" + note.getId(), "folder:" + note.getFolder().getId(), "IN_FOLDER", "folder"));
            }
            for (Tag tag : note.getTags()) {
                nodes.put("tag:" + tag.getId(), new GraphNode("tag:" + tag.getId(), tag.getName(), "tag", tag.getId()));
                links.add(new GraphLink("note:" + note.getId(), "tag:" + tag.getId(), "HAS_TAG", "tag"));
            }
        }

        for (Note note : notes) {
            for (String title : extractWikiLinks(note.getContent())) {
                Note target = titleToNote.get(title);
                if (target != null && !target.getId().equals(note.getId())) {
                    links.add(new GraphLink("note:" + note.getId(), "note:" + target.getId(), "LINKS_TO", "link"));
                }
            }
        }

        for (Schedule schedule : scheduleRepository.findByUserIdOrderByStartTimeDesc(userId)) {
            nodes.put("schedule:" + schedule.getId(), new GraphNode("schedule:" + schedule.getId(), schedule.getTitle(), "schedule", schedule.getId()));
        }

        return new KnowledgeGraphResponse(new ArrayList<>(nodes.values()), links, neo4jEnabled);
    }

    private List<Note> resolveWikiTargets(Note source) {
        List<Note> userNotes = noteRepository.findByUserIdAndDeletedAtIsNull(source.getUser().getId());
        Map<String, Note> titleToNote = new HashMap<>();
        for (Note note : userNotes) {
            titleToNote.putIfAbsent(note.getTitle(), note);
        }

        Set<String> seen = new HashSet<>();
        List<Note> targets = new ArrayList<>();
        for (String title : extractWikiLinks(source.getContent())) {
            Note target = titleToNote.get(title);
            if (target != null && !target.getId().equals(source.getId()) && seen.add(target.getId())) {
                targets.add(target);
            }
        }
        return targets;
    }

    private List<String> extractWikiLinks(String content) {
        List<String> links = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return links;
        }
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            links.add(matcher.group(1).trim());
        }
        return links;
    }

    private void upsertUser(TransactionContext tx, String userId, String username) {
        tx.run("""
                MERGE (u:User {id: $id})
                SET u.username = $username
                """, Map.of("id", userId, "username", username));
    }

    private void upsertNote(TransactionContext tx, Note note) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", note.getId());
        params.put("title", note.getTitle());
        params.put("contentPreview", preview(note.getContent()));
        params.put("deleted", note.getDeletedAt() != null);
        params.put("updatedAt", note.getUpdatedAt());
        tx.run("""
                MERGE (n:Note {id: $id})
                SET n.title = $title,
                    n.contentPreview = $contentPreview,
                    n.deleted = $deleted,
                    n.updatedAt = $updatedAt
                """, params);
    }

    private void upsertFolder(TransactionContext tx, Folder folder) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", folder.getId());
        params.put("name", folder.getName());
        params.put("updatedAt", folder.getUpdatedAt());
        tx.run("""
                MERGE (f:Folder {id: $id})
                SET f.name = $name,
                    f.updatedAt = $updatedAt
                """, params);
    }

    private void upsertTag(TransactionContext tx, Tag tag) {
        tx.run("""
                MERGE (t:Tag {id: $id})
                SET t.name = $name
                """, Map.of("id", tag.getId(), "name", tag.getName()));
    }

    private void upsertSchedule(TransactionContext tx, Schedule schedule) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", schedule.getId());
        params.put("title", schedule.getTitle());
        params.put("description", schedule.getDescription());
        params.put("startTime", schedule.getStartTime());
        params.put("endTime", schedule.getEndTime());
        params.put("status", schedule.getStatus());
        tx.run("""
                MERGE (s:Schedule {id: $id})
                SET s.title = $title,
                    s.description = $description,
                    s.startTime = $startTime,
                    s.endTime = $endTime,
                    s.status = $status
                """, params);
    }

    /**
     * Sync extracted concepts for a note into Neo4j.
     * Creates Concept nodes and (Note)-[:ABOUT]->(Concept) relationships.
     */
    public void syncNoteConcepts(String noteId, List<Map<String, Object>> concepts) {
        if (!isNeo4jEnabled()) return;
        runWrite(tx -> {
            // Remove old concept relationships for this note
            tx.run("MATCH (n:Note {id: $noteId})-[r:ABOUT]->() DELETE r",
                    Map.of("noteId", noteId));

            for (Map<String, Object> c : concepts) {
                String concept = (String) c.get("concept");
                String category = (String) c.getOrDefault("category", "keyword");
                double confidence = ((Number) c.getOrDefault("confidence", 0.8)).doubleValue();

                tx.run("""
                        MERGE (c:Concept {name: $concept})
                        ON CREATE SET c.category = $category
                        WITH c
                        MATCH (n:Note {id: $noteId})
                        MERGE (n)-[r:ABOUT]->(c)
                        SET r.confidence = $confidence
                        """,
                        Map.of("noteId", noteId, "concept", concept,
                                "category", category, "confidence", confidence));
            }
            return null;
        });
        log.debug("Synced {} concepts for note {}", concepts.size(), noteId);
    }

    /**
     * Find notes that share concepts with the given concept list.
     */
    public List<String> findNotesBySharedConcepts(String userId, List<String> concepts, int limit) {
        if (!isNeo4jEnabled() || concepts.isEmpty()) return List.of();
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(n:Note)-[:ABOUT]->(c:Concept)
                        WHERE c.name IN $concepts AND coalesce(n.deleted, false) = false
                        WITH n, count(DISTINCT c) AS sharedCount
                        ORDER BY sharedCount DESC
                        LIMIT $limit
                        RETURN n.id AS noteId
                        """,
                        Map.of("userId", userId, "concepts", concepts, "limit", limit));
                return result.list(r -> r.get("noteId").asString());
            });
        } catch (Exception e) {
            log.error("Failed to find notes by shared concepts: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all concepts for a user, grouped by frequency.
     */
    public List<Map<String, Object>> getUserConceptCloud(String userId, int limit) {
        if (!isNeo4jEnabled()) return List.of();
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(n:Note)-[:ABOUT]->(c:Concept)
                        WHERE coalesce(n.deleted, false) = false
                        WITH c.name AS concept, c.category AS category, count(n) AS noteCount
                        ORDER BY noteCount DESC
                        LIMIT $limit
                        RETURN concept, category, noteCount
                        """,
                        Map.of("userId", userId, "limit", limit));
                List<Map<String, Object>> list = new ArrayList<>();
                result.forEachRemaining(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("concept", r.get("concept").asString());
                    m.put("category", r.get("category").asString());
                    m.put("noteCount", r.get("noteCount").asInt());
                    list.add(m);
                });
                return list;
            });
        } catch (Exception e) {
            log.error("Failed to get concept cloud: {}", e.getMessage());
            return List.of();
        }
    }

    private void deleteNode(String label, String id) {
        if (!isNeo4jEnabled()) {
            return;
        }
        runWrite(tx -> {
            tx.run("MATCH (n:" + label + " {id: $id}) DETACH DELETE n", Map.of("id", id));
            return null;
        });
    }

    private <T> T runWrite(GraphWriteCallback<T> callback) {
        if (!isNeo4jEnabled()) {
            return null;
        }
        try (Session session = driver.session()) {
            return session.executeWrite(callback::apply);
        } catch (Exception e) {
            log.warn("Neo4j write skipped: {}", e.getMessage());
            return null;
        }
    }

    private GraphNode toGraphNode(Node node) {
        List<String> labels = new ArrayList<>();
        node.labels().forEach(labels::add);
        String rawId = node.get("id").asString();
        String type = graphType(labels);
        String label = node.get("title").isNull()
                ? node.get("name").isNull() ? rawId : node.get("name").asString()
                : node.get("title").asString();
        return new GraphNode(type + ":" + rawId, label, type, rawId);
    }

    private String graphId(List<String> labels, String rawId) {
        return graphType(labels) + ":" + rawId;
    }

    private String graphType(List<String> labels) {
        if (labels.contains("Note")) return "note";
        if (labels.contains("Folder")) return "folder";
        if (labels.contains("Tag")) return "tag";
        if (labels.contains("Schedule")) return "schedule";
        return "node";
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String linkLabel(String type) {
        return switch (type) {
            case "HAS_TAG" -> "tag";
            case "IN_FOLDER" -> "folder";
            case "LINKS_TO" -> "link";
            case "PARENT_OF" -> "parent";
            default -> type;
        };
    }

    @FunctionalInterface
    private interface GraphWriteCallback<T> {
        T apply(TransactionContext tx);
    }
}
