package com.ainote.app.controller;

import com.ainote.app.model.graph.KnowledgeGraphResponse;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.KnowledgeGraphService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final SecurityUtils securityUtils;

    public GraphController(KnowledgeGraphService knowledgeGraphService, SecurityUtils securityUtils) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public KnowledgeGraphResponse getGraph() {
        return knowledgeGraphService.getGraph(securityUtils.getCurrentUserId());
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Void> rebuildGraph() {
        knowledgeGraphService.rebuildUserGraph(securityUtils.getCurrentUserId());
        return ResponseEntity.accepted().build();
    }
}
