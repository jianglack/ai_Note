package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves $stepN.result.xxx references in step params
 * using output_result from previously completed steps.
 */
@Component
public class StepParamResolver {

    private static final Logger log = LoggerFactory.getLogger(StepParamResolver.class);
    private static final Pattern REF_PATTERN = Pattern.compile("\\$step(\\d+)\\.result(?:\\.([\\w.]+))?");

    private final ObjectMapper objectMapper;

    public StepParamResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String resolve(TaskStep step, Map<Integer, TaskStep> completedSteps) {
        if (step.getInputParams() == null) return null;

        try {
            JsonNode params = objectMapper.readTree(step.getInputParams());
            JsonNode resolved = resolveNode(params, completedSteps);
            return objectMapper.writeValueAsString(resolved);
        } catch (Exception e) {
            log.warn("Param resolution failed for step {}: {}", step.getStepOrder(), e.getMessage());
            return step.getInputParams();
        }
    }

    private JsonNode resolveNode(JsonNode node, Map<Integer, TaskStep> completedSteps) throws Exception {
        if (node.isTextual()) {
            String text = node.asText();
            Matcher matcher = REF_PATTERN.matcher(text);
            if (matcher.find()) {
                int refOrder = Integer.parseInt(matcher.group(1));
                String fieldPath = matcher.group(2);

                TaskStep refStep = completedSteps.get(refOrder);
                if (refStep == null || refStep.getOutputResult() == null) {
                    throw new RuntimeException("Referenced step " + refOrder + " has no result");
                }

                JsonNode refResult = objectMapper.readTree(refStep.getOutputResult());

                if (matcher.start() == 0 && matcher.end() == text.length()) {
                    return fieldPath != null ? navigatePath(refResult, fieldPath) : refResult;
                }

                String resolvedValue = fieldPath != null
                        ? navigatePath(refResult, fieldPath).asText()
                        : refResult.toString();
                return new TextNode(text.substring(0, matcher.start()) + resolvedValue + text.substring(matcher.end()));
            }
            return node;
        }

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                obj.set(entry.getKey(), resolveNode(entry.getValue(), completedSteps));
            }
            return obj;
        }

        if (node.isArray()) {
            var arr = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                arr.add(resolveNode(item, completedSteps));
            }
            return arr;
        }

        return node;
    }

    private JsonNode navigatePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode()) break;
            current = current.get(segment);
        }
        return current != null ? current : objectMapper.nullNode();
    }
}
