package com.ainote.ai.agent.pending;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PendingActionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PendingActionRegistry.class);
    private static final String MARKER = "PENDING_ACTION:";

    private final ObjectMapper objectMapper;

    public PendingActionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> extractActions(String text) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return actions;
        }

        for (String jsonPayload : extractJsonPayloads(text)) {
            try {
                JsonNode node = objectMapper.readTree(jsonPayload);
                validate(node).ifPresent(actions::add);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse PENDING_ACTION JSON: {}", jsonPayload, e);
            }
        }
        return actions;
    }

    public Optional<JsonNode> parseFirstSubmittedAction(String actionJson) {
        if (actionJson == null || actionJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(actionJson);
            JsonNode candidate = root.isArray()
                    ? (root.isEmpty() ? null : root.get(0))
                    : (root.isObject() ? root : null);
            if (candidate == null || validate(candidate).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(candidate);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse pending action feedback: {}", actionJson, e);
            return Optional.empty();
        }
    }

    public String removeMarkers(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder cleaned = new StringBuilder();
        int position = 0;
        while (position < text.length()) {
            int markerIndex = text.indexOf(MARKER, position);
            if (markerIndex < 0) {
                cleaned.append(text, position, text.length());
                break;
            }

            cleaned.append(text, position, markerIndex);
            int jsonStart = skipWhitespace(text, markerIndex + MARKER.length());
            int jsonEnd = findJsonObjectEnd(text, jsonStart);
            if (jsonEnd < 0) {
                cleaned.append(text, markerIndex, text.length());
                break;
            }
            position = jsonEnd;
        }

        return cleaned.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private List<String> extractJsonPayloads(String text) {
        List<String> payloads = new ArrayList<>();
        int position = 0;
        while (position < text.length()) {
            int markerIndex = text.indexOf(MARKER, position);
            if (markerIndex < 0) {
                break;
            }

            int jsonStart = skipWhitespace(text, markerIndex + MARKER.length());
            int jsonEnd = findJsonObjectEnd(text, jsonStart);
            if (jsonEnd < 0) {
                break;
            }

            payloads.add(text.substring(jsonStart, jsonEnd));
            position = jsonEnd;
        }
        return payloads;
    }

    private Optional<Map<String, Object>> validate(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }

        String rawType = node.path("type").asText("");
        Optional<PendingActionType> type = PendingActionType.from(rawType);
        if (type.isEmpty()) {
            log.warn("Rejected unknown PENDING_ACTION type: {}", rawType);
            return Optional.empty();
        }

        for (String requiredField : type.get().requiredFields()) {
            if (node.path(requiredField).asText("").isBlank()) {
                log.warn("Rejected PENDING_ACTION {} missing required field {}", rawType, requiredField);
                return Optional.empty();
            }
        }

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", type.get().name());
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("type".equals(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            action.put(entry.getKey(), value.isValueNode()
                    ? value.asText()
                    : objectMapper.convertValue(value, Object.class));
        }
        return Optional.of(action);
    }

    private int skipWhitespace(String text, int index) {
        int current = index;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private int findJsonObjectEnd(String text, int start) {
        if (start < 0 || start >= text.length() || text.charAt(start) != '{') {
            return -1;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }
}
