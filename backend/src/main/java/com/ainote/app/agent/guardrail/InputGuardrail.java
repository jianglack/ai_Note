package com.ainote.app.agent.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入安全护栏 — 在用户查询进入 Agent 之前进行安全检查。
 * <ul>
 *   <li>空查询拦截</li>
 *   <li>超长查询拦截（防止 token 浪费）</li>
 *   <li>Prompt Injection 检测（中英文常见注入模式）</li>
 * </ul>
 */
@Component
public class InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(InputGuardrail.class);

    private static final int MAX_QUERY_LENGTH = 10_000;

    /**
     * 常见 Prompt Injection 模式（中英文）
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 英文注入
            Pattern.compile("(?i)ignore\\s+(previous|above|all)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)disregard\\s+(previous|above|all)\\s+(instructions?|prompts?)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)pretend\\s+you\\s+are"),
            Pattern.compile("(?i)act\\s+as\\s+(if|a|an)\\s+"),
            Pattern.compile("(?i)system\\s*:\\s*"),
            Pattern.compile("(?i)\\[INST\\]"),
            Pattern.compile("(?i)<\\|im_start\\|>"),
            // 中文注入
            Pattern.compile("忽略(之前|上面|所有|以上)(的)?(指令|提示|规则|要求|限制)"),
            Pattern.compile("无视(之前|上面|所有|以上)(的)?(指令|提示|规则)"),
            Pattern.compile("你现在是"),
            Pattern.compile("假装你是"),
            Pattern.compile("从现在开始你(的|是)"),
            Pattern.compile("请?扮演"),
            Pattern.compile("进入.*模式")
    );

    /**
     * 检查用户输入是否安全。
     *
     * @param query 用户原始查询
     * @return 通过返回 passed()，拦截返回 blocked(reason)
     */
    public GuardrailResult check(String query) {
        // 空查询
        if (query == null || query.isBlank()) {
            return GuardrailResult.blocked("查询不能为空");
        }

        // 超长查询
        if (query.length() > MAX_QUERY_LENGTH) {
            log.warn("Input guardrail: query too long ({} chars)", query.length());
            return GuardrailResult.blocked("查询过长（超过 " + MAX_QUERY_LENGTH + " 字符），请精简后重试");
        }

        // Prompt Injection 检测
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(query).find()) {
                log.warn("Input guardrail: prompt injection detected, pattern={}, query={}",
                        pattern.pattern(), query.substring(0, Math.min(100, query.length())));
                return GuardrailResult.blocked("检测到不安全的输入，请重新描述您的需求");
            }
        }

        return GuardrailResult.ok();
    }
}
