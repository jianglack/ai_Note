package com.ainote.app.model.graph;

import java.util.List;

public record KnowledgeGraphResponse(
        List<GraphNode> nodes,
        List<GraphLink> links,
        boolean neo4jEnabled) {
}
