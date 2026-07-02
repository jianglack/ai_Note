package com.ainote.app.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownStructureParserTest {

    private MarkdownStructureParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarkdownStructureParser();
    }

    @Test
    void parse_headings_createsSectionNodesWithBreadcrumbs() {
        DocumentStructure structure = parser.parse("""
                # Project
                Intro text.

                ## Scope
                Scope text.
                """);

        assertThat(structure.size()).isEqualTo(2);
        assertThat(structure.getNodes().get(0).getTitle()).isEqualTo("Project");
        assertThat(structure.getNodes().get(0).getLevel()).isEqualTo(1);
        assertThat(structure.getNodes().get(0).getType()).isEqualTo(StructureNode.NodeType.HEADING);
        assertThat(structure.getNodes().get(0).getBreadcrumb()).isEqualTo("Project");
        assertThat(structure.getNodes().get(1).getTitle()).isEqualTo("Scope");
        assertThat(structure.getNodes().get(1).getBreadcrumb()).isEqualTo("Project > Scope");
    }

    @Test
    void parse_withoutHeadings_returnsSingleParagraphNode() {
        DocumentStructure structure = parser.parse("plain paragraph");

        assertThat(structure.size()).isEqualTo(1);
        assertThat(structure.getNodes().get(0).getType()).isEqualTo(StructureNode.NodeType.PARAGRAPH);
        assertThat(structure.getNodes().get(0).getText()).isEqualTo("plain paragraph");
    }
}
