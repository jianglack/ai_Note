package com.ainote.app.controller;

import com.ainote.app.model.graph.GraphNode;
import com.ainote.app.model.graph.KnowledgeGraphResponse;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.KnowledgeGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphControllerTest {

    private KnowledgeGraphService knowledgeGraphService;
    private SecurityUtils securityUtils;
    private GraphController controller;

    @BeforeEach
    void setUp() {
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new GraphController(knowledgeGraphService, securityUtils);
    }

    @Test
    void getGraphUsesCurrentUserAndReturnsOnlyServiceResponse() {
        KnowledgeGraphResponse response = new KnowledgeGraphResponse(
                List.of(new GraphNode("note:note-1", "Owned note", "note", "note-1")),
                List.of(),
                false);

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(knowledgeGraphService.getGraph("user-1")).thenReturn(response);

        KnowledgeGraphResponse result = controller.getGraph();

        assertThat(result.nodes()).extracting(GraphNode::refId).containsExactly("note-1");
        assertThat(result.neo4jEnabled()).isFalse();
        verify(knowledgeGraphService).getGraph("user-1");
    }

    @Test
    void rebuildGraphIsAcceptedAndScopedToCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");

        ResponseEntity<Void> result = controller.rebuildGraph();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(knowledgeGraphService).rebuildUserGraph("user-1");
    }
}
