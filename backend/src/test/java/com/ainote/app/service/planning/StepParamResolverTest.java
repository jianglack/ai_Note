package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StepParamResolver")
class StepParamResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StepParamResolver resolver = new StepParamResolver(objectMapper);

    @Test
    void resolvesMultipleReferencesInsideOneString() throws Exception {
        TaskStep completedOne = new TaskStep();
        completedOne.setOutputResult("{\"title\":\"Alpha\"}");
        TaskStep completedTwo = new TaskStep();
        completedTwo.setOutputResult("{\"id\":\"note-2\"}");

        TaskStep step = new TaskStep();
        step.setStepOrder(3);
        step.setInputParams("""
                {"summary":"created $step1.result.title with $step2.result.id"}
                """);

        String resolved = resolver.resolve(step, Map.of(1, completedOne, 2, completedTwo));

        JsonNode root = objectMapper.readTree(resolved);
        assertThat(root.path("summary").asText()).isEqualTo("created Alpha with note-2");
    }

    @Test
    void preservesWholeReferenceJsonType() throws Exception {
        TaskStep completed = new TaskStep();
        completed.setOutputResult("{\"items\":[\"a\",\"b\"]}");

        TaskStep step = new TaskStep();
        step.setStepOrder(2);
        step.setInputParams("""
                {"items":"$step1.result.items"}
                """);

        String resolved = resolver.resolve(step, Map.of(1, completed));

        JsonNode items = objectMapper.readTree(resolved).path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(2);
    }
}
