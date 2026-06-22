package com.ainote.app.chunking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构解析器
 * 解析 Markdown 文档的层级结构
 */
@Component
public class MarkdownStructureParser {
    
    private static final Logger log = LoggerFactory.getLogger(MarkdownStructureParser.class);
    
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```([\\s\\S]*?)```");
    
    /**
     * 解析 Markdown 文档结构
     */
    public DocumentStructure parse(String content) {
        DocumentStructure structure = new DocumentStructure();
        
        // 查找所有标题
        List<HeaderInfo> headers = extractHeaders(content);
        
        if (headers.isEmpty()) {
            // 没有标题，作为单个段落处理
            structure.addNode(StructureNode.builder()
                    .text(content)
                    .type(StructureNode.NodeType.PARAGRAPH)
                    .build());
            return structure;
        }
        
        // 维护标题层级路径
        List<String> breadcrumbPath = new ArrayList<>();
        int[] lastLevelIndex = new int[7]; // 支持 h1-h6
        
        for (int i = 0; i < headers.size(); i++) {
            HeaderInfo header = headers.get(i);
            int level = header.level;
            
            // 更新面包屑路径
            updateBreadcrumbPath(breadcrumbPath, lastLevelIndex, level, header.title);
            
            // 提取该标题下的内容
            int contentStart = header.endPos;
            int contentEnd = (i + 1 < headers.size()) ? headers.get(i + 1).startPos : content.length();
            String sectionContent = content.substring(contentStart, contentEnd).trim();
            
            // 如果内容不为空，创建节点
            if (!sectionContent.isEmpty()) {
                structure.addNode(StructureNode.builder()
                        .text(sectionContent)
                        .title(header.title)
                        .level(level)
                        .type(StructureNode.NodeType.HEADING)
                        .breadcrumbPath(new ArrayList<>(breadcrumbPath))
                        .build());
            }
        }
        
        log.debug("Parsed Markdown structure: {} sections", structure.size());
        return structure;
    }
    
    /**
     * 提取所有标题信息
     */
    private List<HeaderInfo> extractHeaders(String content) {
        List<HeaderInfo> headers = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(content);
        
        while (matcher.find()) {
            int level = matcher.group(1).length();
            String title = matcher.group(2).trim();
            int startPos = matcher.start();
            int endPos = matcher.end();
            
            headers.add(new HeaderInfo(level, title, startPos, endPos));
        }
        
        return headers;
    }
    
    /**
     * 更新面包屑路径
     */
    private void updateBreadcrumbPath(List<String> breadcrumbPath, int[] lastLevelIndex, int level, String title) {
        // 清除比当前级别更深的路径
        while (breadcrumbPath.size() >= level) {
            breadcrumbPath.remove(breadcrumbPath.size() - 1);
        }
        
        // 添加当前标题
        breadcrumbPath.add(title);
        lastLevelIndex[level] = breadcrumbPath.size() - 1;
    }
    
    /**
     * 标题信息
     */
    private static class HeaderInfo {
        final int level;
        final String title;
        final int startPos;
        final int endPos;
        
        HeaderInfo(int level, String title, int startPos, int endPos) {
            this.level = level;
            this.title = title;
            this.startPos = startPos;
            this.endPos = endPos;
        }
    }
}
