package com.ainote.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Knowledge Graph Service for Neo4j operations.
 * Provides graph-based relationship queries including:
 * - CO_TAGGED: Notes sharing 2+ common tags
 * - TEMPORAL: Notes created on the same day
 * - Second-degree relationship discovery
 * - Path finding between notes
 */
@Slf4j
@Service
public class KnowledgeGraphService {

    private final Driver driver;

    public KnowledgeGraphService(ObjectProvider<Driver> driverProvider) {
        this.driver = driverProvider.getIfAvailable();
    }

    @PostConstruct
    public void ensureConstraints() {
        if (!isNeo4jEnabled()) {
            log.info("Neo4j is disabled, skipping constraint initialization");
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
        log.info("Neo4j constraints and indexes initialized");
    }

    public boolean isNeo4jEnabled() {
        return driver != null;
    }

    /**
     * Get the full user knowledge graph including nodes and relationships.
     */
    public Map<String, Object> getUserGraph(String userId) {
        if (!isNeo4jEnabled()) {
            return Map.of("nodes", List.of(), "links", List.of(), "neo4jEnabled", false);
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                List<Map<String, Object>> nodes = new ArrayList<>();
                List<Map<String, Object>> links = new ArrayList<>();

                // Fetch nodes
                tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(source)
                        WHERE NOT source:Note OR coalesce(source.deleted, false) = false
                        OPTIONAL MATCH (source)-[r:HAS_TAG|IN_FOLDER|LINKS_TO|PARENT_OF|CO_TAGGED|TEMPORAL]->(target)
                        WHERE target IS NULL OR NOT target:Note OR coalesce(target.deleted, false) = false
                        WITH source, target
                        UNWIND [source, target] AS node
                        WITH DISTINCT node
                        WHERE node IS NOT NULL
                        RETURN node
                        """, Map.of("userId", userId)).forEachRemaining(record -> {
                    nodes.add(toNodeMap(record.get("node").asNode()));
                });

                // Fetch relationships
                tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(source)-[r:HAS_TAG|IN_FOLDER|LINKS_TO|PARENT_OF|CO_TAGGED|TEMPORAL]->(target)
                        WHERE (NOT source:Note OR coalesce(source.deleted, false) = false)
                          AND (NOT target:Note OR coalesce(target.deleted, false) = false)
                        RETURN source.id AS sourceId, labels(source) AS sourceLabels,
                               target.id AS targetId, labels(target) AS targetLabels,
                               type(r) AS type, properties(r) AS props
                        """, Map.of("userId", userId)).forEachRemaining(record -> {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("source", graphId(record.get("sourceLabels").asList(v -> v.asString()), record.get("sourceId").asString()));
                    link.put("target", graphId(record.get("targetLabels").asList(v -> v.asString()), record.get("targetId").asString()));
                    link.put("type", record.get("type").asString());
                    link.put("label", linkLabel(record.get("type").asString()));
                    if (!record.get("props").isNull()) {
                        link.put("properties", record.get("props").asMap());
                    }
                    links.add(link);
                });

                return Map.of("nodes", nodes, "links", links, "neo4jEnabled", true);
            });
        } catch (Exception e) {
            log.warn("Neo4j graph query failed: {}", e.getMessage());
            return Map.of("nodes", List.of(), "links", List.of(), "neo4jEnabled", true, "error", e.getMessage());
        }
    }

    /**
     * Build CO_TAGGED relationships between notes sharing 2+ tags.
     */
    public void buildCoTaggedRelationships(String userId) {
        if (!isNeo4jEnabled()) {
            return;
        }
        runWrite(tx -> {
            // Remove existing CO_TAGGED relationships for user
            tx.run("""
                    MATCH (u:User {id: $userId})-[:OWNS]->(n1:Note)-[r:CO_TAGGED]->(n2:Note)
                    DELETE r
                    """, Map.of("userId", userId));

            // Create new CO_TAGGED relationships for notes sharing 2+ tags
            tx.run("""
                    MATCH (u:User {id: $userId})-[:OWNS]->(n1:Note)-[:HAS_TAG]->(t:Tag)<-[:HAS_TAG]-(n2:Note)<-[:OWNS]-(u)
                    WHERE coalesce(n1.deleted, false) = false
                      AND coalesce(n2.deleted, false) = false
                      AND id(n1) < id(n2)
                    WITH n1, n2, count(t) AS sharedTags
                    WHERE sharedTags >= 2
                    MERGE (n1)-[:CO_TAGGED {weight: sharedTags}]->(n2)
                    """, Map.of("userId", userId));
            return null;
        });
        log.debug("Built CO_TAGGED relationships for user {}", userId);
    }

    /**
     * Build TEMPORAL relationships between notes created on the same day.
     */
    public void buildTemporalRelationships(String userId) {
        if (!isNeo4jEnabled()) {
            return;
        }
        runWrite(tx -> {
            // Remove existing TEMPORAL relationships for user
            tx.run("""
                    MATCH (u:User {id: $userId})-[:OWNS]->(n1:Note)-[r:TEMPORAL]->(n2:Note)
                    DELETE r
                    """, Map.of("userId", userId));

            // Create new TEMPORAL relationships for notes created on the same day
            tx.run("""
                    MATCH (u:User {id: $userId})-[:OWNS]->(n1:Note),
                          (u)-[:OWNS]->(n2:Note)
                    WHERE coalesce(n1.deleted, false) = false
                      AND coalesce(n2.deleted, false) = false
                      AND id(n1) < id(n2)
                      AND n1.createdAt IS NOT NULL
                      AND n2.createdAt IS NOT NULL
                      AND date(n1.createdAt) = date(n2.createdAt)
                    MERGE (n1)-[:TEMPORAL]->(n2)
                    """, Map.of("userId", userId));
            return null;
        });
        log.debug("Built TEMPORAL relationships for user {}", userId);
    }

    /**
     * Find second-degree related notes: notes connected to the given note's neighbors.
     */
    public List<Map<String, Object>> findSecondDegreeRelated(String noteId, String userId) {
        if (!isNeo4jEnabled()) {
            return List.of();
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                List<Map<String, Object>> results = new ArrayList<>();
                tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(source:Note {id: $noteId})
                        WHERE coalesce(source.deleted, false) = false
                        MATCH (source)-[:HAS_TAG|IN_FOLDER|LINKS_TO|CO_TAGGED|TEMPORAL*1..2]-(related:Note)
                        WHERE related.id <> $noteId
                          AND coalesce(related.deleted, false) = false
                          AND (u)-[:OWNS]->(related)
                        WITH DISTINCT related,
                             CASE
                               WHEN (source)-[:LINKS_TO|HAS_TAG|IN_FOLDER]-(related) THEN 1
                               ELSE 2
                             END AS degree
                        RETURN related.id AS id, related.title AS title, degree
                        ORDER BY degree, related.updatedAt DESC
                        LIMIT 20
                        """, Map.of("noteId", noteId, "userId", userId)).forEachRemaining(record -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", record.get("id").asString());
                    node.put("title", record.get("title").asString());
                    node.put("degree", record.get("degree").asInt());
                    results.add(node);
                });
                return results;
            });
        } catch (Exception e) {
            log.warn("Second-degree query failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Find the shortest path between two notes.
     */
    public List<Map<String, Object>> findPath(String fromNoteId, String toNoteId, String userId) {
        if (!isNeo4jEnabled()) {
            return List.of();
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                List<Map<String, Object>> pathNodes = new ArrayList<>();
                var result = tx.run("""
                        MATCH (u:User {id: $userId})-[:OWNS]->(start:Note {id: $fromId}),
                              (u)-[:OWNS]->(end:Note {id: $toId})
                        WHERE coalesce(start.deleted, false) = false
                          AND coalesce(end.deleted, false) = false
                        MATCH path = shortestPath((start)-[:HAS_TAG|IN_FOLDER|LINKS_TO|CO_TAGGED|TEMPORAL*..10]-(end))
                        RETURN path
                        LIMIT 1
                        """, Map.of("fromId", fromNoteId, "toId", toNoteId, "userId", userId));

                if (result.hasNext()) {
                    Record record = result.next();
                    Path path = record.get("path").asPath();
                    path.nodes().forEach(node -> {
                        Map<String, Object> nodeMap = new LinkedHashMap<>();
                        nodeMap.put("id", node.get("id").asString());
                        nodeMap.put("labels", node.labels());
                        if (!node.get("title").isNull()) {
                            nodeMap.put("title", node.get("title").asString());
                        }
                        if (!node.get("name").isNull()) {
                            nodeMap.put("name", node.get("name").asString());
                        }
                        pathNodes.add(nodeMap);
                    });
                }
                return pathNodes;
            });
        } catch (Exception e) {
            log.warn("Path finding failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Rebuild the entire graph for a user including derived relationships.
     */
    public void rebuildUserGraph(String userId) {
        if (!isNeo4jEnabled()) {
            log.info("Neo4j is disabled, skipping graph rebuild");
            return;
        }

        log.info("Rebuilding graph for user {}", userId);

        // Clear existing user graph
        runWrite(tx -> {
            tx.run("""
                    MATCH (u:User {id: $userId})-[:OWNS]->(n)
                    DETACH DELETE n
                    """, Map.of("userId", userId));
            return null;
        });

        // Note: In microservices architecture, actual note/folder/schedule sync
        // should be triggered via events from note-service/schedule-service.
        // Here we just rebuild derived relationships if nodes exist.

        // Rebuild derived relationships
        buildCoTaggedRelationships(userId);
        buildTemporalRelationships(userId);

        log.info("Graph rebuild complete for user {}", userId);
    }

    /**
     * Search for related note IDs using graph traversal.
     */
    public List<String> searchRelatedNoteIds(String userId, String query, List<String> seedNoteIds, int limit) {
        if (!isNeo4jEnabled()) {
            return List.of();
        }

        List<String> safeSeedIds = seedNoteIds == null ? List.of() : seedNoteIds;

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new HashMap<>();
                params.put("userId", userId);
                params.put("query", sanitizeFullTextQuery(query));
                params.put("seedIds", safeSeedIds);
                params.put("limit", limit);

                List<String> ids = new ArrayList<>();
                tx.run("""
                        MATCH (:User {id: $userId})-[:OWNS]->(seed:Note)
                        WHERE seed.id IN $seedIds AND coalesce(seed.deleted, false) = false
                        WITH collect(seed) AS vectorSeeds
                        CALL {
                          WITH vectorSeeds
                          UNWIND vectorSeeds AS seed
                          OPTIONAL MATCH (seed)-[:LINKS_TO|HAS_TAG|IN_FOLDER|CO_TAGGED|TEMPORAL]-(related:Note)
                          WHERE coalesce(related.deleted, false) = false
                          RETURN collect(seed) + collect(related) AS vectorMatches
                        }
                        CALL {
                          WITH vectorSeeds
                          CALL db.index.fulltext.queryNodes("ainote_note_text", $query, {limit: $limit}) YIELD node, score
                          MATCH (:User {id: $userId})-[:OWNS]->(node)
                          WHERE coalesce(node.deleted, false) = false
                          OPTIONAL MATCH (node)-[:LINKS_TO|HAS_TAG|IN_FOLDER|CO_TAGGED|TEMPORAL]-(neighbor:Note)
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
            log.warn("Neo4j graph search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ============= Helper Methods =============

    private <T> T runWrite(GraphWriteCallback<T> callback) {
        if (!isNeo4jEnabled()) {
            return null;
        }
        try (Session session = driver.session()) {
            return session.executeWrite(callback::apply);
        } catch (Exception e) {
            log.warn("Neo4j write operation failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toNodeMap(Node node) {
        List<String> labels = new ArrayList<>();
        node.labels().forEach(labels::add);
        String rawId = node.get("id").asString();
        String type = graphType(labels);
        String label = node.get("title").isNull()
                ? node.get("name").isNull() ? rawId : node.get("name").asString()
                : node.get("title").asString();

        Map<String, Object> nodeMap = new LinkedHashMap<>();
        nodeMap.put("id", type + ":" + rawId);
        nodeMap.put("label", label);
        nodeMap.put("type", type);
        nodeMap.put("rawId", rawId);
        return nodeMap;
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

    private String linkLabel(String type) {
        return switch (type) {
            case "HAS_TAG" -> "tag";
            case "IN_FOLDER" -> "folder";
            case "LINKS_TO" -> "link";
            case "PARENT_OF" -> "parent";
            case "CO_TAGGED" -> "co-tagged";
            case "TEMPORAL" -> "temporal";
            default -> type;
        };
    }

    private String sanitizeFullTextQuery(String query) {
        if (query == null || query.isBlank()) {
            return "*";
        }
        String sanitized = query.replaceAll("[+\\-!(){}\\[\\]^\"~*?:\\\\/]|&&|\\|\\|", " ").trim();
        return sanitized.isBlank() ? "*" : sanitized;
    }

    @FunctionalInterface
    private interface GraphWriteCallback<T> {
        T apply(TransactionContext tx);
    }
}
