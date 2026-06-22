package com.ainote.app.chunking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码结构解析器
 * 解析代码的函数、类等结构
 */
@Component
public class CodeStructureParser {
    
    private static final Logger log = LoggerFactory.getLogger(CodeStructureParser.class);
    
    // 函数/方法模式（支持多种语言）
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(public|private|protected|static|async)?\\s*(function|def|fn)?\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{",
        Pattern.MULTILINE
    );
    
    // 类模式
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(public|private|protected)?\\s*(class|interface|struct)\\s+(\\w+)",
        Pattern.MULTILINE
    );
    
    /**
     * 解析代码结构
     */
    public DocumentStructure parse(String content) {
        DocumentStructure structure = new DocumentStructure();
        
        // 尝试按类分割
        List<CodeBlock> classes = extractClasses(content);
        
        if (!classes.isEmpty()) {
            for (CodeBlock classBlock : classes) {
                // 提取类中的方法
                List<CodeBlock> methods = extractFunctions(classBlock.content);
                
                if (methods.isEmpty()) {
                    // 类中没有方法，整个类作为一个节点
                    structure.addNode(StructureNode.builder()
                            .text(classBlock.content)
                            .title(classBlock.name)
                            .type(StructureNode.NodeType.CODE_BLOCK)
                            .addToBreadcrumb(classBlock.name)
                            .build());
                } else {
                    // 为每个方法创建节点
                    for (CodeBlock method : methods) {
                        structure.addNode(StructureNode.builder()
                                .text(method.content)
                                .title(method.name)
                                .type(StructureNode.NodeType.CODE_BLOCK)
                                .addToBreadcrumb(classBlock.name)
                                .addToBreadcrumb(method.name)
                                .build());
                    }
                }
            }
        } else {
            // 没有类，尝试按函数分割
            List<CodeBlock> functions = extractFunctions(content);
            
            if (functions.isEmpty()) {
                // 没有明确的函数，作为单个代码块
                structure.addNode(StructureNode.builder()
                        .text(content)
                        .type(StructureNode.NodeType.CODE_BLOCK)
                        .build());
            } else {
                for (CodeBlock function : functions) {
                    structure.addNode(StructureNode.builder()
                            .text(function.content)
                            .title(function.name)
                            .type(StructureNode.NodeType.CODE_BLOCK)
                            .addToBreadcrumb(function.name)
                            .build());
                }
            }
        }
        
        log.debug("Parsed code structure: {} blocks", structure.size());
        return structure;
    }
    
    /**
     * 提取类定义
     */
    private List<CodeBlock> extractClasses(String content) {
        List<CodeBlock> classes = new ArrayList<>();
        Matcher matcher = CLASS_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String className = matcher.group(3);
            int startPos = matcher.start();
            
            // 查找类的结束位置（匹配大括号）
            int endPos = findMatchingBrace(content, startPos);
            
            if (endPos > startPos) {
                String classContent = content.substring(startPos, endPos);
                classes.add(new CodeBlock(className, classContent));
            }
        }
        
        return classes;
    }
    
    /**
     * 提取函数定义
     */
    private List<CodeBlock> extractFunctions(String content) {
        List<CodeBlock> functions = new ArrayList<>();
        Matcher matcher = FUNCTION_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String functionName = matcher.group(3);
            int startPos = matcher.start();
            
            // 查找函数的结束位置（匹配大括号）
            int endPos = findMatchingBrace(content, startPos);
            
            if (endPos > startPos) {
                String functionContent = content.substring(startPos, endPos);
                functions.add(new CodeBlock(functionName, functionContent));
            }
        }
        
        return functions;
    }
    
    /**
     * 查找匹配的右大括号
     */
    private int findMatchingBrace(String content, int startPos) {
        int braceCount = 0;
        boolean foundOpenBrace = false;
        
        for (int i = startPos; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (c == '{') {
                braceCount++;
                foundOpenBrace = true;
            } else if (c == '}') {
                braceCount--;
                if (foundOpenBrace && braceCount == 0) {
                    return i + 1;
                }
            }
        }
        
        return content.length();
    }
    
    /**
     * 代码块信息
     */
    private static class CodeBlock {
        final String name;
        final String content;
        
        CodeBlock(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }
}
