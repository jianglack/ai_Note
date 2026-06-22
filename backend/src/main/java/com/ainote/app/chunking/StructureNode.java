package com.ainote.app.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构节点
 * 表示文档中的一个结构单元（如章节、段落、代码块等）
 */
public class StructureNode {
    private final String text;
    private final String title;
    private final int level;
    private final NodeType type;
    private final List<String> breadcrumbPath;
    
    public enum NodeType {
        HEADING,      // 标题
        PARAGRAPH,    // 段落
        CODE_BLOCK,   // 代码块
        LIST,         // 列表
        TABLE,        // 表格
        QUOTE         // 引用
    }
    
    private StructureNode(Builder builder) {
        this.text = builder.text;
        this.title = builder.title;
        this.level = builder.level;
        this.type = builder.type;
        this.breadcrumbPath = builder.breadcrumbPath;
    }
    
    public String getText() {
        return text;
    }
    
    public String getTitle() {
        return title;
    }
    
    public int getLevel() {
        return level;
    }
    
    public NodeType getType() {
        return type;
    }
    
    public List<String> getBreadcrumbPath() {
        return breadcrumbPath;
    }
    
    /**
     * 获取面包屑字符串
     * 例如：Java学习笔记 > 第一章 > 基础语法
     */
    public String getBreadcrumb() {
        return String.join(" > ", breadcrumbPath);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String text;
        private String title;
        private int level = 0;
        private NodeType type = NodeType.PARAGRAPH;
        private List<String> breadcrumbPath = new ArrayList<>();
        
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        
        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }
        
        public Builder breadcrumbPath(List<String> path) {
            this.breadcrumbPath = new ArrayList<>(path);
            return this;
        }
        
        public Builder addToBreadcrumb(String item) {
            this.breadcrumbPath.add(item);
            return this;
        }
        
        public StructureNode build() {
            return new StructureNode(this);
        }
    }
}
