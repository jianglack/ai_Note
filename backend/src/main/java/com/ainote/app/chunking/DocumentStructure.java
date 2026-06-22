package com.ainote.app.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构
 * 表示文档的层级结构树
 */
public class DocumentStructure {
    private final List<StructureNode> nodes;
    
    public DocumentStructure() {
        this.nodes = new ArrayList<>();
    }
    
    public void addNode(StructureNode node) {
        this.nodes.add(node);
    }
    
    public List<StructureNode> getNodes() {
        return nodes;
    }
    
    public int size() {
        return nodes.size();
    }
}
