package com.ainote.app.chunking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 内容类型检测器
 * 自动识别文档的内容类型
 */
@Component
public class ContentTypeDetector {
    
    private static final Logger log = LoggerFactory.getLogger(ContentTypeDetector.class);
    
    // Markdown 特征模式
    private static final Pattern MARKDOWN_HEADER = Pattern.compile("^#{1,6}\\s+.+$", Pattern.MULTILINE);
    private static final Pattern MARKDOWN_LIST = Pattern.compile("^[-*+]\\s+.+$", Pattern.MULTILINE);
    private static final Pattern MARKDOWN_CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[.+?\\]\\(.+?\\)");
    
    // 代码特征模式
    private static final Pattern CODE_FUNCTION = Pattern.compile("(function|def|public|private|protected)\\s+\\w+\\s*\\(");
    private static final Pattern CODE_CLASS = Pattern.compile("(class|interface|struct)\\s+\\w+");
    private static final Pattern CODE_IMPORT = Pattern.compile("^(import|#include|using|require)\\s+", Pattern.MULTILINE);
    
    /**
     * 检测内容类型
     */
    public ContentType detect(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ContentType.PLAIN_TEXT;
        }
        
        int markdownScore = calculateMarkdownScore(content);
        int codeScore = calculateCodeScore(content);
        
        log.debug("Content type scores - Markdown: {}, Code: {}", markdownScore, codeScore);
        
        // 判断逻辑
        if (markdownScore >= 3) {
            return ContentType.MARKDOWN;
        } else if (codeScore >= 3) {
            return ContentType.CODE;
        } else if (markdownScore > 0 || codeScore > 0) {
            return ContentType.MIXED;
        } else {
            return ContentType.PLAIN_TEXT;
        }
    }
    
    /**
     * 计算 Markdown 特征分数
     */
    private int calculateMarkdownScore(String content) {
        int score = 0;
        
        // 包含标题
        if (MARKDOWN_HEADER.matcher(content).find()) {
            score += 2;
        }
        
        // 包含列表
        if (MARKDOWN_LIST.matcher(content).find()) {
            score += 1;
        }
        
        // 包含代码块
        if (MARKDOWN_CODE_BLOCK.matcher(content).find()) {
            score += 1;
        }
        
        // 包含链接
        if (MARKDOWN_LINK.matcher(content).find()) {
            score += 1;
        }
        
        // 包含表格
        if (content.contains("|") && content.contains("---")) {
            score += 1;
        }
        
        return score;
    }
    
    /**
     * 计算代码特征分数
     */
    private int calculateCodeScore(String content) {
        int score = 0;
        
        // 包含函数定义
        if (CODE_FUNCTION.matcher(content).find()) {
            score += 2;
        }
        
        // 包含类定义
        if (CODE_CLASS.matcher(content).find()) {
            score += 2;
        }
        
        // 包含导入语句
        if (CODE_IMPORT.matcher(content).find()) {
            score += 1;
        }
        
        // 包含大括号（代码块）
        if (content.contains("{") && content.contains("}")) {
            score += 1;
        }
        
        // 包含分号（语句结束符）
        long semicolonCount = content.chars().filter(ch -> ch == ';').count();
        if (semicolonCount > 3) {
            score += 1;
        }
        
        return score;
    }
}
