package com.ainote.app.chunking;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构感知文档分块器
 * 根据文档类型和结构智能分块，保留语义完整性和层级信息
 */
@Component
public class StructureAwareDocumentSplitter implements DocumentSplitter {
    
    private static final Logger log = LoggerFactory.getLogger(StructureAwareDocumentSplitter.class);
    
    // 分块参数
    private static final int MAX_CHUNK_SIZE = 800;      // 最大分块大小（字符）
    private static final int OVERLAP_SIZE = 100;        // 重叠大小
    private static final int MIN_CHUNK_SIZE = 200;      // 最小分块大小
    
    private final ContentTypeDetector contentTypeDetector;
    private final MarkdownStructureParser markdownParser;
    private final CodeStructureParser codeParser;
    
    // 回退分块器（用于过大的块）
    private final DocumentSplitter fallbackSplitter;
    
    public StructureAwareDocumentSplitter(
            ContentTypeDetector contentTypeDetector,
            MarkdownStructureParser markdownParser,
            CodeStructureParser codeParser) {
        this.contentTypeDetector = contentTypeDetector;
        this.markdownParser = markdownParser;
        this.codeParser = codeParser;
        this.fallbackSplitter = DocumentSplitters.recursive(MAX_CHUNK_SIZE, OVERLAP_SIZE);
        
        log.info("StructureAwareDocumentSplitter initialized with maxChunkSize: {}, overlap: {}", 
                MAX_CHUNK_SIZE, OVERLAP_SIZE);
    }
    
    @Override
    public List<TextSegment> split(Document document) {
        String content = document.text();
        Metadata baseMetadata = document.metadata();
        
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }
        
        // 1. 检测内容类型
        ContentType contentType = contentTypeDetector.detect(content);
        log.debug("Detected content type: {}", contentType);
        
        // 2. 根据类型选择解析策略
        DocumentStructure structure = parseStructure(content, contentType);
        
        // 3. 将结构节点转换为文本段
        List<TextSegment> segments = new ArrayList<>();
        int globalChunkIndex = 0;
        
        for (StructureNode node : structure.getNodes()) {
            String nodeText = node.getText();
            
            // 如果节点过大，需要进一步分割
            if (nodeText.length() > MAX_CHUNK_SIZE) {
                List<TextSegment> subSegments = splitLargeNode(node, baseMetadata, globalChunkIndex);
                segments.addAll(subSegments);
                globalChunkIndex += subSegments.size();
            } else if (nodeText.length() >= MIN_CHUNK_SIZE) {
                // 节点大小合适，直接创建段
                segments.add(createTextSegment(node, baseMetadata, globalChunkIndex, 0, 1));
                globalChunkIndex++;
            } else {
                // 节点过小，尝试与下一个节点合并
                // 这里简化处理，直接创建段
                segments.add(createTextSegment(node, baseMetadata, globalChunkIndex, 0, 1));
                globalChunkIndex++;
            }
        }
        
        log.info("Split document into {} segments (type: {})", segments.size(), contentType);
        return segments;
    }
    
    /**
     * 根据内容类型解析结构
     */
    private DocumentStructure parseStructure(String content, ContentType contentType) {
        return switch (contentType) {
            case MARKDOWN -> markdownParser.parse(content);
            case CODE -> codeParser.parse(content);
            case MIXED -> parseMixedContent(content);
            case PLAIN_TEXT -> parsePlainText(content);
        };
    }
    
    /**
     * 解析混合内容（Markdown + 代码）
     */
    private DocumentStructure parseMixedContent(String content) {
        // 优先尝试 Markdown 解析
        DocumentStructure structure = markdownParser.parse(content);
        
        // 如果没有找到结构，回退到纯文本
        if (structure.size() == 0) {
            return parsePlainText(content);
        }
        
        return structure;
    }
    
    /**
     * 解析纯文本（按段落分割）
     */
    private DocumentStructure parsePlainText(String content) {
        DocumentStructure structure = new DocumentStructure();
        
        // 按双换行符分割段落
        String[] paragraphs = content.split("\n\n+");
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                structure.addNode(StructureNode.builder()
                        .text(trimmed)
                        .type(StructureNode.NodeType.PARAGRAPH)
                        .build());
            }
        }
        
        // 如果没有段落，整个内容作为一个节点
        if (structure.size() == 0) {
            structure.addNode(StructureNode.builder()
                    .text(content)
                    .type(StructureNode.NodeType.PARAGRAPH)
                    .build());
        }
        
        return structure;
    }
    
    /**
     * 分割过大的节点
     */
    private List<TextSegment> splitLargeNode(StructureNode node, Metadata baseMetadata, int startIndex) {
        List<TextSegment> segments = new ArrayList<>();
        
        // 使用回退分块器分割
        Document tempDoc = Document.from(node.getText());
        List<TextSegment> subSegments = fallbackSplitter.split(tempDoc);
        
        for (int i = 0; i < subSegments.size(); i++) {
            TextSegment segment = createTextSegment(
                    node, 
                    baseMetadata, 
                    startIndex + i, 
                    i, 
                    subSegments.size()
            );
            
            // 替换文本为子段的文本
            segment = TextSegment.from(subSegments.get(i).text(), segment.metadata());
            segments.add(segment);
        }
        
        return segments;
    }
    
    /**
     * 创建文本段
     */
    private TextSegment createTextSegment(
            StructureNode node, 
            Metadata baseMetadata, 
            int chunkIndex,
            int subIndex,
            int totalSubChunks) {
        
        // 构建元数据
        Metadata metadata = baseMetadata.copy();
        
        // 添加分块索引
        metadata.put("chunkIndex", String.valueOf(chunkIndex));
        
        // 添加子分块信息（如果有）
        if (totalSubChunks > 1) {
            metadata.put("subIndex", String.valueOf(subIndex));
            metadata.put("totalSubChunks", String.valueOf(totalSubChunks));
        }
        
        // 添加结构信息
        if (node.getTitle() != null && !node.getTitle().isEmpty()) {
            metadata.put("title", node.getTitle());
        }
        
        if (node.getLevel() > 0) {
            metadata.put("level", String.valueOf(node.getLevel()));
        }
        
        metadata.put("nodeType", node.getType().name());
        
        // 添加面包屑路径
        if (!node.getBreadcrumbPath().isEmpty()) {
            metadata.put("breadcrumb", node.getBreadcrumb());
            
            // 分别存储路径的各个部分（便于过滤和搜索）
            for (int i = 0; i < node.getBreadcrumbPath().size(); i++) {
                metadata.put("breadcrumb_" + i, node.getBreadcrumbPath().get(i));
            }
        }
        
        return TextSegment.from(node.getText(), metadata);
    }
    
    @Override
    public List<TextSegment> splitAll(List<Document> documents) {
        return documents.stream()
                .flatMap(doc -> split(doc).stream())
                .toList();
    }
}
