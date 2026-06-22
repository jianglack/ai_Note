package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.entity.EpisodicMemory;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import com.ainote.app.repository.EpisodicMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 上下文组装器
 *
 * 简化为两级意图：CHAT（问候）和 STANDARD（其他一切）。
 * 不再用正则/LLM 区分 OPERATION vs QUERY —— 该决策交给 Agent(LLM) 自己判断。
 * 意图检测仅用于优化上下文加载量，不影响工具选择。
 */
@Service
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private static final Set<String> GREETING_PATTERNS = Set.of(
            "你好", "谢谢", "好的", "嗯", "确认", "取消", "是", "否",
            "ok", "hi", "hello", "thanks", "yes", "no", "额", "哦"
    );

    /**
     * 简化意图分类：
     * CHAT: 问候/确认等短文本，只需轻量上下文
     * STANDARD: 其他一切请求，统一组装完整上下文，由 Agent 自行决定是执行、建议还是查询
     */
    public enum Intent { CHAT, STANDARD }

    private final NoteRepository noteRepository;
    private final FolderRepository folderRepository;
    private final LangChain4jRagService ragService;
    private final SemanticMemoryRepository semanticMemoryRepository;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final JiTokenService jiTokenService;
    private final RagFeedbackService ragFeedbackService;

    @Value("${app.context.note-max-chars:800}")
    private int noteMaxChars;

    @Value("${app.context.snippet-max-chars:300}")
    private int snippetMaxChars;

    @Value("${app.context.total-budget-tokens:4000}")
    private int totalBudgetTokens;

    @Value("${app.context.rag-min-score:0.7}")
    private double ragMinScore;

    @Value("${app.context.budget.semantic-memory:500}")
    private int budgetSemanticMemory;

    @Value("${app.context.budget.episodic-memory:400}")
    private int budgetEpisodicMemory;

    @Value("${app.context.budget.user-overview:300}")
    private int budgetUserOverview;

    @Value("${app.context.budget.selected-notes:1500}")
    private int budgetSelectedNotes;

    @Value("${app.context.budget.rag-context:800}")
    private int budgetRagContext;

    public ContextAssembler(NoteRepository noteRepository,
                            FolderRepository folderRepository,
                            LangChain4jRagService ragService,
                            SemanticMemoryRepository semanticMemoryRepository,
                            EpisodicMemoryRepository episodicMemoryRepository,
                            JiTokenService jiTokenService,
                            RagFeedbackService ragFeedbackService) {
        this.noteRepository = noteRepository;
        this.folderRepository = folderRepository;
        this.ragService = ragService;
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.jiTokenService = jiTokenService;
        this.ragFeedbackService = ragFeedbackService;
    }

    /**
     * 意图检测（极简版）
     * 只区分问候和非问候。"该不该调工具"的决策交给 Agent 系统提示词。
     */
    public Intent detectIntent(String query) {
        if (query == null || query.isBlank()) return Intent.CHAT;
        String trimmed = query.trim();
        if (trimmed.length() <= 6 && GREETING_PATTERNS.stream().anyMatch(p -> trimmed.equalsIgnoreCase(p))) {
            return Intent.CHAT;
        }
        return Intent.STANDARD;
    }

    /**
     * 组装上下文
     * CHAT: 仅语义记忆（轻量快速）
     * STANDARD: 统一组装完整上下文（语义记忆 + 情节记忆 + 用户概览 + 选中笔记 + 条件RAG）
     */
    public String assemble(String query, List<String> noteIds, String userId) {
        Intent intent = detectIntent(query);
        log.info("Detected intent: {} for query: '{}'", intent, query);

        StringBuilder context = new StringBuilder();
        int usedTokens = 0;

        // CHAT: 仅语义记忆
        if (intent == Intent.CHAT) {
            String semantic = truncateToTokenBudget(buildSemanticMemory(userId), budgetSemanticMemory);
            context.append(semantic);
            String result = context.toString();
            log.info("Context assembled (CHAT): {} chars, ~{} tokens", result.length(), estimateTokens(result));
            return result;
        }

        // STANDARD: 统一完整上下文，让 Agent 自行判断该执行、建议还是查询

        // 优先级 1：语义记忆（用户偏好/事实）
        String semantic = truncateToTokenBudget(buildSemanticMemory(userId), budgetSemanticMemory);
        context.append(semantic);
        usedTokens += estimateTokens(semantic);

        // 优先级 2：情节记忆（最近会话摘要）
        String episodic = truncateToTokenBudget(buildEpisodicMemory(userId), budgetEpisodicMemory);
        context.append(episodic);
        usedTokens += estimateTokens(episodic);

        // 优先级 3：用户概览（统计 + 文件夹列表）
        String overview = truncateToTokenBudget(buildUserOverview(userId), budgetUserOverview);
        context.append(overview);
        usedTokens += estimateTokens(overview);

        // 优先级 4：选中笔记（用户选了特定笔记时）
        if (noteIds != null && !noteIds.isEmpty()) {
            int noteBudget = Math.min(budgetSelectedNotes, totalBudgetTokens - usedTokens);
            String notes = truncateToTokenBudget(buildSelectedNotes(noteIds, userId), noteBudget);
            context.append(notes);
            usedTokens += estimateTokens(notes);
        }

        // 优先级 5：RAG 片段（查询有实质内容时）
        if (query != null && isSubstantiveQuery(query)) {
            int ragBudget = Math.min(budgetRagContext, totalBudgetTokens - usedTokens);
            if (ragBudget > 100) {
                String rag = truncateToTokenBudget(buildRagContext(query, userId), ragBudget);
                context.append(rag);
                usedTokens += estimateTokens(rag);
            }
        }

        String result = context.toString();
        log.info("Context assembled (STANDARD): {} chars, ~{} tokens (budget: {})", result.length(), usedTokens, totalBudgetTokens);
        return result;
    }

    private String buildUserOverview(String userId) {
        StringBuilder overview = new StringBuilder();
        try {
            List<Note> allNotes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
            List<Folder> folders = folderRepository.findByUserId(userId);

            overview.append("<user_overview>\n");
            overview.append("  <statistics>\n");
            overview.append("    <total_notes>").append(allNotes.size()).append("</total_notes>\n");
            overview.append("    <total_folders>").append(folders.size()).append("</total_folders>\n");
            overview.append("  </statistics>\n");

            if (!folders.isEmpty()) {
                overview.append("  <folders>\n");
                for (Folder folder : folders) {
                    overview.append("    <folder id=\"").append(escapeXml(folder.getId())).append("\">");
                    overview.append(escapeXml(folder.getName()));
                    overview.append("</folder>\n");
                }
                overview.append("  </folders>\n");
            }

            overview.append("</user_overview>\n\n");
        } catch (Exception e) {
            log.warn("Failed to build user overview", e);
            overview.append("<user_overview><error>无法加载</error></user_overview>\n\n");
        }
        return overview.toString();
    }

    private String buildSelectedNotes(List<String> noteIds, String userId) {
        StringBuilder notes = new StringBuilder();
        notes.append("<selected_notes default_operation_target=\"true\">\n");
        notes.append("  <selection_policy>")
                .append("用户未明确指定其他标题或ID时，删除、修改、移动、打标签等笔记操作必须优先作用于 selected_notes 中的笔记；")
                .append("不要把 rag_context 中的其他笔记当作当前操作对象。")
                .append("</selection_policy>\n");

        int index = 1;
        for (String noteId : noteIds) {
            Optional<Note> noteOpt = noteRepository.findByIdAndUserIdWithTagsAndFolder(noteId, userId);
            if (noteOpt.isEmpty()) continue;

            Note note = noteOpt.get();
            notes.append("  <note index=\"").append(index).append("\" id=\"")
                    .append(escapeXml(note.getId())).append("\">\n");
            notes.append("    <title>").append(escapeXml(note.getTitle())).append("</title>\n");

            String content = cleanHtml(note.getContent());
            String truncated = smartTruncate(content, noteMaxChars);
            notes.append("    <content><![CDATA[\n").append(truncated).append("\n    ]]></content>\n");

            if (note.getTags() != null && !note.getTags().isEmpty()) {
                notes.append("    <tags>\n");
                for (var tag : note.getTags()) {
                    notes.append("      <tag>").append(escapeXml(tag.getName())).append("</tag>\n");
                }
                notes.append("    </tags>\n");
            }

            if (note.getFolder() != null) {
                notes.append("    <folder id=\"").append(escapeXml(note.getFolder().getId())).append("\">");
                notes.append(escapeXml(note.getFolder().getName()));
                notes.append("</folder>\n");
            }

            notes.append("  </note>\n");
            index++;
        }

        notes.append("</selected_notes>\n\n");
        return notes.toString();
    }

    private String buildRagContext(String query, String userId) {
        try {
            double effectiveThreshold = ragFeedbackService.getAdaptiveThreshold();
            List<com.ainote.app.model.Note> results = ragService.searchWithMinScore(query, 5, effectiveThreshold, userId);
            if (results.isEmpty()) return "";

            StringBuilder rag = new StringBuilder();
            rag.append("<rag_context role=\"reference_only\" operation_target=\"false\">\n");
            for (var note : results) {
                String snippet = smartTruncate(cleanHtml(note.getContent()), snippetMaxChars);
                rag.append("  <snippet noteId=\"").append(escapeXml(note.getId()))
                        .append("\" title=\"").append(escapeXml(note.getTitle()))
                        .append("\" operation_target=\"false\">\n");
                rag.append("    ").append(snippet).append("\n");
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

        String[] paragraphs = content.split("\n\n");
        StringBuilder result = new StringBuilder();
        int charCount = 0;
        int paragraphCount = 0;

        for (String para : paragraphs) {
            if (charCount + para.length() > maxChars) {
                if (paragraphCount >= 2 || charCount > maxChars * 0.6) break;
                int remaining = maxChars - charCount;
                if (remaining > 100) {
                    result.append(para, 0, remaining).append("...");
                }
                break;
            }
            result.append(para).append("\n\n");
            charCount += para.length() + 2;
            paragraphCount++;
        }

        if (paragraphCount < paragraphs.length) {
            result.append("\n[... 还有 ")
                    .append(paragraphs.length - paragraphCount)
                    .append(" 段内容未显示]");
        }

        return result.toString().trim();
    }

    private int estimateTokens(String text) {
        return jiTokenService.countTokens(text);
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
        return html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("</div>", "\n")
                .replaceAll("</li>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String buildSemanticMemory(String userId) {
        try {
            List<SemanticMemory> memories = semanticMemoryRepository
                    .findTopByUserId(userId, PageRequest.of(0, 10));

            if (memories.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("<user_memory>\n");
            for (SemanticMemory mem : memories) {
                sb.append("  <").append(mem.getCategory()).append(">");
                sb.append(escapeXml(mem.getContent()));
                sb.append("</").append(mem.getCategory()).append(">\n");
            }
            sb.append("</user_memory>\n\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build semantic memory context", e);
            return "";
        }
    }

    private String buildEpisodicMemory(String userId) {
        try {
            List<EpisodicMemory> episodes = episodicMemoryRepository
                    .findRecentByUserId(userId, PageRequest.of(0, 3));

            if (episodes.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("<recent_sessions>\n");
            for (EpisodicMemory ep : episodes) {
                sb.append("  <session time=\"").append(ep.getCreatedAt()).append("\">");
                sb.append(escapeXml(ep.getSessionSummary()));
                sb.append("</session>\n");
            }
            sb.append("</recent_sessions>\n\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build episodic memory context", e);
            return "";
        }
    }
}
