package com.ainote.app.agent.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输出安全护栏 — 在 Agent 回复返回给用户之前进行脱敏处理。
 * <ul>
 *   <li>API Key / Secret 脱敏</li>
 *   <li>Bearer Token 脱敏</li>
 *   <li>数据库连接串脱敏</li>
 *   <li>JWT Token 脱敏</li>
 * </ul>
 */
@Component
public class OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(OutputGuardrail.class);

    private static final String REDACTED = "[REDACTED]";

    /**
     * 不应出现在回复中的敏感信息模式
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // API Key / Secret / Password / Token 赋值
            Pattern.compile("(?i)(api[_-]?key|secret[_-]?key|password|token|credential)\\s*[:=]\\s*[\"']?\\S{8,}"),
            // Common API key formats
            Pattern.compile("sk-[a-zA-Z0-9]{20,}"),
            Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9._\\-]{20,}"),
            // JWT Token（三段 base64）
            Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}"),
            // 数据库连接串
            Pattern.compile("(?i)(jdbc|mongodb|redis)://[^\\s]{10,}")
    );

    /**
     * 对 Agent 回复进行脱敏处理。
     *
     * @param response      原始回复
     * @param currentUserId 当前用户 ID（预留，用于未来用户级脱敏规则）
     * @return 脱敏后的回复
     */
    public String sanitize(String response, String currentUserId) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        String result = response;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(result).find()) {
                log.warn("Output guardrail: sensitive pattern detected and redacted in response for user={}",
                        currentUserId);
                result = pattern.matcher(result).replaceAll(REDACTED);
            }
        }
        return result;
    }
}
