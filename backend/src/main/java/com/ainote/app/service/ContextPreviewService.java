package com.ainote.app.service;

import com.ainote.app.model.context.ContextPreviewRequest;
import com.ainote.app.model.context.ContextPreviewResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContextPreviewService {

    private final ContextAssembler contextAssembler;
    private final JiTokenService jiTokenService;

    public ContextPreviewService(ContextAssembler contextAssembler, JiTokenService jiTokenService) {
        this.contextAssembler = contextAssembler;
        this.jiTokenService = jiTokenService;
    }

    public ContextPreviewResponse preview(String userId, ContextPreviewRequest request) {
        String query = request.query();
        List<String> noteIds = request.noteIds() == null ? List.of() : List.copyOf(request.noteIds());
        ContextAssembler.Intent intent = contextAssembler.detectIntent(query);
        String finalContext = contextAssembler.assemble(query, noteIds, userId);
        List<ContextPreviewResponse.Section> sections = extractSections(finalContext);
        int selectedNoteCount = noteIds.size();

        return new ContextPreviewResponse(
                query,
                noteIds,
                selectedNoteCount,
                intent.name(),
                finalContext.length(),
                jiTokenService.countTokens(finalContext),
                finalContext,
                sections,
                buildFlow(intent, selectedNoteCount, sections));
    }

    private List<ContextPreviewResponse.Section> extractSections(String context) {
        List<ContextPreviewResponse.Section> sections = new ArrayList<>();
        addSection(sections, context, "semantic_memory", "长期记忆", "<user_memory", "</user_memory>");
        addSection(sections, context, "episodic_memory", "最近会话摘要", "<recent_sessions", "</recent_sessions>");
        addSection(sections, context, "user_overview", "用户项目概览", "<user_overview", "</user_overview>");
        addSection(sections, context, "selected_notes", "选中笔记", "<selected_notes", "</selected_notes>");
        addSection(sections, context, "rag_context", "RAG 参考片段", "<rag_context", "</rag_context>");
        return sections;
    }

    private void addSection(List<ContextPreviewResponse.Section> sections,
                            String context,
                            String type,
                            String label,
                            String startMarker,
                            String endMarker) {
        String content = extractBlock(context, startMarker, endMarker);
        if (content.isBlank()) {
            return;
        }
        sections.add(new ContextPreviewResponse.Section(
                type,
                label,
                true,
                jiTokenService.countTokens(content),
                content));
    }

    private String extractBlock(String context, String startMarker, String endMarker) {
        int start = context.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int end = context.indexOf(endMarker, start);
        if (end < 0) {
            return context.substring(start).trim();
        }
        return context.substring(start, end + endMarker.length()).trim();
    }

    private List<ContextPreviewResponse.FlowStep> buildFlow(ContextAssembler.Intent intent,
                                                            int selectedNoteCount,
                                                            List<ContextPreviewResponse.Section> sections) {
        List<ContextPreviewResponse.FlowStep> flow = new ArrayList<>();
        flow.add(new ContextPreviewResponse.FlowStep(
                1,
                "判断问题类型",
                intent == ContextAssembler.Intent.CHAT ? "CHAT：轻量上下文" : "STANDARD：完整上下文",
                "active"));
        flow.add(new ContextPreviewResponse.FlowStep(
                2,
                "检查选中笔记",
                selectedNoteCount > 0 ? "收到 " + selectedNoteCount + " 个选中笔记 ID" : "没有选中笔记",
                selectedNoteCount > 0 ? "active" : "skipped"));
        flow.add(sectionStep(3, "加入长期记忆", "semantic_memory", sections));
        flow.add(sectionStep(4, "加入最近会话摘要", "episodic_memory", sections));
        flow.add(sectionStep(5, "加入用户项目概览", "user_overview", sections));
        flow.add(sectionStep(6, "加入选中笔记", "selected_notes", sections));
        flow.add(sectionStep(7, "判断是否需要 RAG", "rag_context", sections));
        flow.add(new ContextPreviewResponse.FlowStep(
                8,
                "生成最终上下文",
                "最终上下文 " + totalTokens(sections) + " tokens，" + sections.size() + " 个分段",
                "active"));
        return flow;
    }

    private ContextPreviewResponse.FlowStep sectionStep(int order,
                                                        String title,
                                                        String sectionType,
                                                        List<ContextPreviewResponse.Section> sections) {
        return sections.stream()
                .filter(section -> section.type().equals(sectionType))
                .findFirst()
                .map(section -> new ContextPreviewResponse.FlowStep(
                        order,
                        title,
                        "已注入，约 " + section.estimatedTokens() + " tokens",
                        "active"))
                .orElseGet(() -> new ContextPreviewResponse.FlowStep(
                        order,
                        title,
                        "未注入：无匹配内容或被当前策略跳过",
                        "skipped"));
    }

    private int totalTokens(List<ContextPreviewResponse.Section> sections) {
        return sections.stream()
                .mapToInt(ContextPreviewResponse.Section::estimatedTokens)
                .sum();
    }
}
