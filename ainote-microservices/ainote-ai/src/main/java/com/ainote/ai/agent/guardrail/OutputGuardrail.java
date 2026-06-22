package com.ainote.ai.agent.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(OutputGuardrail.class);

    private static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|secret[_-]?key|password|token|credential)\\s*[:=]\\s*[\"']?\\S{8,}"),
            Pattern.compile("sk-[a-zA-Z0-9]{20,}"),
            Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9._\\-]{20,}"),
            Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}"),
            Pattern.compile("(?i)(jdbc|mongodb|redis)://[^\\s]{10,}")
    );

    public String sanitize(String response, String currentUserId) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        String result = response;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(result).find()) {
                log.warn("Output guardrail: sensitive pattern detected and redacted for user={}", currentUserId);
                result = pattern.matcher(result).replaceAll(REDACTED);
            }
        }
        return result;
    }
}
