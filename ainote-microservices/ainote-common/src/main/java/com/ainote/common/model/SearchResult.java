package com.ainote.common.model;

import lombok.Data;

import java.util.List;

@Data
public class SearchResult {
    private Long noteId;
    private String title;
    private String snippet;
    private double score;
    private List<String> highlights;
    private String source;
}
