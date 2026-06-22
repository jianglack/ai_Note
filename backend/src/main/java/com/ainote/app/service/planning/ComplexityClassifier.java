package com.ainote.app.service.planning;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule-based complexity classifier. No LLM cost.
 * Returns COMPLEX if the query likely needs >2 tool calls.
 */
@Component
public class ComplexityClassifier {

    public enum Complexity { SIMPLE, COMPLEX }

    private static final List<String> CONNECTIVES = List.of(
            "并且", "然后", "之后", "接着", "同时", "再",
            "首先.*然后", "先.*再", "第一.*第二",
            "then", "after that", "finally", "and then"
    );

    private static final List<String> BATCH_KEYWORDS = List.of(
            "所有", "批量", "每个", "每条", "全部", "逐个", "一一"
    );

    private static final List<String> COMPOUND_KEYWORDS = List.of(
            "整理", "归纳", "分析并", "总结并", "分类并", "导出并",
            "清理", "重新组织", "迁移"
    );

    private static final List<String> ACTION_VERBS = List.of(
            "创建", "新建", "搜索", "查找", "查询", "删除", "移动",
            "复制", "合并", "修改", "更新", "添加", "移除", "标签",
            "重命名", "归档", "导出", "分类", "提取"
    );

    // 咨询/建议类关键词 — 出现时说明用户在征求意见而非要求执行
    private static final Pattern ADVISORY_PATTERN = Pattern.compile(
            "想|建议|推荐|怎[么样]|如何|应该|什么.*名[字称]|方[案式法]|思路|规划|学习|需要.*学"
    );

    public Complexity classify(String query) {
        if (query == null || query.length() < 10) return Complexity.SIMPLE;

        long verbCount = ACTION_VERBS.stream().filter(query::contains).count();
        boolean hasWorkflowSignal = CONNECTIVES.stream()
                .anyMatch(pattern -> Pattern.compile(pattern).matcher(query).find())
                || BATCH_KEYWORDS.stream().anyMatch(query::contains)
                || COMPOUND_KEYWORDS.stream().anyMatch(query::contains);

        // 咨询/建议类查询交给 Agent 文字回答；但不能拦截明确包含执行动作的多步任务。
        if (!hasWorkflowSignal && verbCount == 0 && ADVISORY_PATTERN.matcher(query).find()) {
            return Complexity.SIMPLE;
        }

        for (String pattern : CONNECTIVES) {
            if (Pattern.compile(pattern).matcher(query).find()) {
                return Complexity.COMPLEX;
            }
        }

        for (String kw : BATCH_KEYWORDS) {
            if (query.contains(kw)) {
                long actionCount = ACTION_VERBS.stream().filter(query::contains).count();
                if (actionCount >= 1) return Complexity.COMPLEX;
            }
        }

        for (String kw : COMPOUND_KEYWORDS) {
            if (query.contains(kw)) return Complexity.COMPLEX;
        }

        if (verbCount >= 3) return Complexity.COMPLEX;

        return Complexity.SIMPLE;
    }
}
