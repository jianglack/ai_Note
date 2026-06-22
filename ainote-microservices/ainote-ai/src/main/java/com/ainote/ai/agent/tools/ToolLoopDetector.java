package com.ainote.ai.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolLoopDetector {

    private static final Logger log = LoggerFactory.getLogger(ToolLoopDetector.class);

    private final int maxRepeatedCalls;

    private static final ThreadLocal<List<String>> CALL_SIGNATURES = ThreadLocal.withInitial(ArrayList::new);

    public ToolLoopDetector(@Value("${app.agent.tool-loop-max-repeated:5}") int maxRepeatedCalls) {
        this.maxRepeatedCalls = maxRepeatedCalls;
    }

    public void reset() {
        CALL_SIGNATURES.remove();
    }

    public boolean recordAndCheck(String toolName, String action, String params) {
        String signature = toolName + ":" + action + ":" + params;
        List<String> signatures = CALL_SIGNATURES.get();
        signatures.add(signature);

        long count = signatures.stream().filter(s -> s.equals(signature)).count();
        if (count > maxRepeatedCalls) {
            log.warn("Tool loop detected: {} called {} times (limit: {})", signature, count, maxRepeatedCalls);
            return true;
        }
        return false;
    }
}
