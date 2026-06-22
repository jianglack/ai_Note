package com.ainote.ai.service;

import com.ainote.ai.feign.NoteClient;
import com.ainote.ai.feign.SearchClient;
import com.ainote.common.model.SearchResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 上下文组装器（微服务版）
 * 通过 Feign 客户端获取笔记/文件夹数据，组装上下文
 */
@Service
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private static final Set<String> GREETING_PATTERNS = Set.of(
            "你好", "谢谢", "好的", "嗯", "确认", "取消", "是", "否",
            "ok", "hi", "hello", "thanks", "yes", "no", "额", "哦"
    );

    public enum Intent { OPERATION, QUERY, CHAT }

    private static final Pattern OPERATION_PATTERN = Pattern.compile(
            "(创建|新建|建一?个|删除|删掉|移除|更新|修改|改一下|移动|移到|复制|合并|清空|恢复|添加标签|去掉标签|" +
            "帮我[建创写]|设[个一]|加[个一]|记录一下|建个|设置提醒|提醒我|新增|归档)"
    );

    private static final Pattern QUERY_PATTERN = Pattern.compile(
            "(搜索|查找|查一下|找一下|找到|有哪些|有什么|列出|显示|看看|是什么|什么是|怎么|如何|为什么|" +
            "关于.{0,10}的笔记|哪些笔记|笔记.*内容|总结|概括|分析)"
    );

    private final NoteClient noteClient;
    private final SearchClient searchClient;
    private final ChatModel chatModel;

    @Value("${app.context.intent.llm-fallback:true}")
    private boolean intentLlmFallback;

    @Value("${app.context.note-max-chars:800}")
    private int noteMaxChars;

    @Value("${app.context.total-budget-tokens:4000}")
    private int totalBudgetTokens;

    @Value("${app.context.rag-min-score:0.7}")
    private double ragMinScore;

    @Value("${app.context.budget.user-overview:300}")
    private int budgetUserOverview;

    @Value("${app.context.budget.selected-notes:1500}")
    private int budgetSelectedNotes;

    @Value("${app.context.budget.rag-context:800}")
    private int budgetRagContext;

    public ContextAssembler(NoteClient noteClient, SearchClient searchClient,
                            ChatModel chatModel) {
        this.noteClient = noteClient;
        this.searchClient = searchClient;
        this.chatModel = chatModel;
    }

    public Intent detectIntent(String query) {
        if (query == null || query.isBlank()) return Intent.CHAT;
        String trimmed = query.trim();

        Intent regexResult = detectIntentByRegex(trimmed);
        if (regexResult != null) return regexResult;

        if (intentLlmFallback) {
            Intent llmResult = detectIntentByLlm(trimmed);
            if (llmResult != null) return llmResult;
        }

        return trimmed.length() <= 20 ? Intent.OPERATION : Intent.QUERY;
    }

    private Intent detectIntentByRegex(String query) {
        if (query.length() <= 6 && GREETING_PATTERNS.stream().anyMatch(p -> query.equalsIgnoreCase(p))) {
            return Intent.CHAT;
        }
        if (OPERATION_PATTERN.matcher(query).find()) return Intent.OPERATION;
        if (QUERY_PATTERN.matcher(query).find()) return Intent.QUERY;
        return null;
    }

    private Intent detectIntentByLlm(String query) {
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("Classify the user intent as one of: OPERATION, QUERY, CHAT. Reply with exactly one word."),
                    UserMessage.from(query)
            );
            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            String result = response.aiMessage().text();
            if (result == null) return null;
            return switch (result.trim().toUpperCase()) {
                case "OPERATION" -> Intent.OPERATION;
                case "QUERY" -> Intent.QUERY;
                case "CHAT" -> Intent.CHAT;
                default -> null;
            };
        } catch (Exception e) {
            log.warn("LLM intent classification failed: {}", e.getMessage());
            return null;
        }
    }

    public String assemble(String query, List<String> noteIds, String userId) {
        Intent intent = detectIntent(query);
        log.info("Detected intent: {} for query: '{}'", intent, query);

        StringBuilder context = new StringBuilder();
        int usedTokens = 0;

        if (intent == Intent.CHAT) {
            return context.toString();
        }

        // User overview (folders list)
        if (intent == Intent.OPERATION || intent == Intent.QUERY) {
            String overview = truncateToTokenBudget(buildUserOverview(), budgetUserOverview);
            context.append(overview);
            usedTokens += estimateTokens(overview);
        }

        // Selected notes
        if (noteIds != null && !noteIds.isEmpty()) {
            int noteBudget = Math.min(budgetSelectedNotes, totalBudgetTokens - usedTokens);
            String notes = truncateToTokenBudget(buildSelectedNotes(noteIds), noteBudget);
            context.append(notes);
            usedTokens += estimateTokens(notes);
        }

        // RAG context (only for QUERY)
        if (intent == Intent.QUERY && query != null && isSubstantiveQuery(query)) {
            int ragBudget = Math.min(budgetRagContext, totalBudgetTokens - usedTokens);
            if (ragBudget > 100) {
                String rag = truncateToTokenBudget(buildRagContext(query), ragBudget);
                context.append(rag);
            }
        }

        String result = context.toString();
        log.info("Context assembled ({}): {} chars", intent, result.length());
        return result;
    }

    private String buildUserOverview() {
        try {
            List<Map<String, Object>> folders = noteClient.listFolders();
            Map<String, Object> noteResult = noteClient.listNotes(0, 1);

            StringBuilder overview = new StringBuilder();
            overview.append("<user_overview>\n");

            Object contentObj = noteResult.get("totalElements");
            if (contentObj != null) {
                overview.append("  <total_notes>").append(contentObj).append("</total_notes>\n");
            }
            overview.append("  <total_folders>").append(folders.size()).append("</total_folders>\n");

            if (!folders.isEmpty()) {
                overview.append("  <folders>\n");
                for (Map<String, Object> folder : folders) {
                    overview.append("    <folder id=\"").append(escapeXml(String.valueOf(folder.get("id"))))
                            .append("\">").append(escapeXml(String.valueOf(folder.get("name"))))
                            .append("</folder>\n");
                }
                overview.append("  </folders>\n");
            }

            overview.append("</user_overview>\n\n");
            return overview.toString();
        } catch (Exception e) {
            log.warn("Failed to build user overview", e);
            return "<user_overview><error>unavailable</error></user_overview>\n\n";
        }
    }

    private String buildSelectedNotes(List<String> noteIds) {
        StringBuilder notes = new StringBuilder();
        notes.append("<selected_notes>\n");
        int index = 1;
        for (String noteId : noteIds) {
            try {
                Map<String, Object> note = noteClient.getNote(noteId);
                if (note.containsKey("error")) continue;

                notes.append("  <note index=\"").append(index).append("\" id=\"")
                        .append(escapeXml(String.valueOf(note.get("id")))).append("\">\n");
                notes.append("    <title>").append(escapeXml(String.valueOf(note.get("title")))).append("</title>\n");

                String content = cleanHtml(String.valueOf(note.getOrDefault("content", "")));
                String truncated = smartTruncate(content, noteMaxChars);
                notes.append("    <content><![CDATA[\n").append(truncated).append("\n    ]]></content>\n");
                notes.append("  </note>\n");
                index++;
            } catch (Exception e) {
                log.warn("Failed to get note {}", noteId, e);
            }
        }
        notes.append("</selected_notes>\n\n");
        return notes.toString();
    }

    private String buildRagContext(String query) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("query", query);
            request.put("limit", 5);
            List<SearchResult> results = searchClient.hybridSearch(request);
            if (results.isEmpty()) return "";

            StringBuilder rag = new StringBuilder();
            rag.append("<rag_context>\n");
            for (SearchResult sr : results) {
                if (sr.getScore() < ragMinScore) continue;
                rag.append("  <snippet noteId=\"").append(sr.getNoteId())
                        .append("\" title=\"").append(escapeXml(sr.getTitle())).append("\">\n");
                rag.append("    ").append(sr.getSnippet()).append("\n");
                rag.append("  </snippet>\n");
            }
            rag.append("</rag_context>\n\n");
            return rag.toString();
        } catch (Exception e) {
            log.warn("RAG context retrieval failed", e);
            return "";
        }
    }

    private boolean isSubstantiveQuery(String query) {
        if (query == null || query.trim().length() < 3) return false;
        String trimmed = query.trim().toLowerCase();
        return GREETING_PATTERNS.stream().noneMatch(p -> trimmed.equals(p));
    }

    private String smartTruncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "...";
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        int cjkChars = 0, otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) cjkChars++;
            else otherChars++;
        }
        return cjkChars / 2 + otherChars / 4;
    }

    private String truncateToTokenBudget(String text, int maxTokens) {
        int tokens = estimateTokens(text);
        if (tokens <= maxTokens) return text;
        double ratio = (double) maxTokens / tokens;
        int maxChars = (int) (text.length() * ratio * 0.9);
        return text.substring(0, Math.min(maxChars, text.length())) + "\n<!-- context truncated -->";
    }

    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&").replaceAll("\n{3,}", "\n\n").trim();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
