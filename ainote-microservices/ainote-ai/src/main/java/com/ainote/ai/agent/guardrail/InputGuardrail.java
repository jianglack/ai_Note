package com.ainote.ai.agent.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(InputGuardrail.class);

    private static final int MAX_QUERY_LENGTH = 10_000;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(previous|above|all)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)disregard\\s+(previous|above|all)\\s+(instructions?|prompts?)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)pretend\\s+you\\s+are"),
            Pattern.compile("(?i)act\\s+as\\s+(if|a|an)\\s+"),
            Pattern.compile("(?i)system\\s*:\\s*"),
            Pattern.compile("(?i)\\[INST\\]"),
            Pattern.compile("(?i)<\\|im_start\\|>"),
            Pattern.compile("忽略(之前|上面|所有|以上)(的)?(指令|提示|规则|要求|限制)"),
            Pattern.compile("无视(之前|上面|所有|以上)(的)?(指令|提示|规则)"),
            Pattern.compile("你现在是"),
            Pattern.compile("假装你是"),
            Pattern.compile("从现在开始你(的|是)"),
            Pattern.compile("请?扮演"),
            Pattern.compile("进入.*模式")
    );

    public GuardrailResult check(String query) {
        if (query == null || query.isBlank()) {
            return GuardrailResult.blocked("查询不能为空");
        }

        if (query.length() > MAX_QUERY_LENGTH) {
            log.warn("Input guardrail: query too long ({} chars)", query.length());
            return GuardrailResult.blocked("查询过长（超过 " + MAX_QUERY_LENGTH + " 字符），请精简后重试");
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(query).find()) {
                log.warn("Input guardrail: prompt injection detected, pattern={}", pattern.pattern());
                return GuardrailResult.blocked("检测到不安全的输入，请重新描述您的需求");
            }
        }

        return GuardrailResult.ok();
    }
}
