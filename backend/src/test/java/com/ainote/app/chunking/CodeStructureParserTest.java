package com.ainote.app.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeStructureParserTest {

    private CodeStructureParser parser;

    @BeforeEach
    void setUp() {
        parser = new CodeStructureParser();
    }

    @Test
    void parse_classWithFunctions_createsMethodNodesWithBreadcrumbs() {
        DocumentStructure structure = parser.parse("""
                class Demo {
                  function save() {
                    return 1;
                  }

                  function load() {
                    return 2;
                  }
                }
                """);

        assertThat(structure.size()).isEqualTo(2);
        assertThat(structure.getNodes()).extracting(StructureNode::getTitle)
                .containsExactly("save", "load");
        assertThat(structure.getNodes().get(0).getType()).isEqualTo(StructureNode.NodeType.CODE_BLOCK);
        assertThat(structure.getNodes().get(0).getBreadcrumb()).isEqualTo("Demo > save");
    }

    @Test
    void parse_unstructuredCode_returnsSingleCodeBlock() {
        DocumentStructure structure = parser.parse("const value = 1;");

        assertThat(structure.size()).isEqualTo(1);
        assertThat(structure.getNodes().get(0).getType()).isEqualTo(StructureNode.NodeType.CODE_BLOCK);
        assertThat(structure.getNodes().get(0).getText()).isEqualTo("const value = 1;");
    }
}
