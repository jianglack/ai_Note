package com.ainote.ai.controller;

import com.ainote.ai.service.KnowledgeGraphService;
import com.ainote.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for knowledge graph operations.
 * Provides endpoints for graph visualization, relationship discovery, and path finding.
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    private String getCurrentUserId() {
        Long userId = UserContext.getCurrentUserId();
        return userId != null ? userId.toString() : "anonymous";
    }

    /**
     * Get the full knowledge graph for the current user.
     * Returns nodes and links for visualization.
     */
    @GetMapping
    public Map<String, Object> getGraph() {
        String userId = getCurrentUserId();
        return knowledgeGraphService.getUserGraph(userId);
    }

    /**
     * Find second-degree related notes for a given note.
     * Returns notes connected through shared tags, folders, links, or temporal proximity.
     */
    @GetMapping("/related/{noteId}")
    public List<Map<String, Object>> getSecondDegree(@PathVariable String noteId) {
        String userId = getCurrentUserId();
        return knowledgeGraphService.findSecondDegreeRelated(noteId, userId);
    }

    /**
     * Find the shortest path between two notes in the knowledge graph.
     * Traverses HAS_TAG, IN_FOLDER, LINKS_TO, CO_TAGGED, and TEMPORAL relationships.
     */
    @GetMapping("/path")
    public List<Map<String, Object>> findPath(@RequestParam String from, @RequestParam String to) {
        String userId = getCurrentUserId();
        return knowledgeGraphService.findPath(from, to, userId);
    }

    /**
     * Rebuild the knowledge graph for the current user.
     * Recreates derived relationships (CO_TAGGED, TEMPORAL).
     */
    @PostMapping("/rebuild")
    public Map<String, String> rebuild() {
        String userId = getCurrentUserId();
        knowledgeGraphService.rebuildUserGraph(userId);
        return Map.of("status", "rebuilt");
    }

    /**
     * Explicitly build CO_TAGGED relationships.
     * Creates edges between notes that share 2 or more tags.
     */
    @PostMapping("/build/co-tagged")
    public Map<String, String> buildCoTagged() {
        String userId = getCurrentUserId();
        knowledgeGraphService.buildCoTaggedRelationships(userId);
        return Map.of("status", "co-tagged relationships built");
    }

    /**
     * Explicitly build TEMPORAL relationships.
     * Creates edges between notes created on the same day.
     */
    @PostMapping("/build/temporal")
    public Map<String, String> buildTemporal() {
        String userId = getCurrentUserId();
        knowledgeGraphService.buildTemporalRelationships(userId);
        return Map.of("status", "temporal relationships built");
    }

    /**
     * Search for related notes using graph traversal.
     */
    @PostMapping("/search")
    public List<String> searchRelated(
            @RequestParam(required = false) String query,
            @RequestBody(required = false) List<String> seedNoteIds,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = getCurrentUserId();
        return knowledgeGraphService.searchRelatedNoteIds(userId, query, seedNoteIds, limit);
    }
}
