package com.ainote.common.model;

import lombok.Data;

@Data
public class SearchRequest {
    private String query;
    private Long userId;
    private int limit = 10;
    private String searchType = "hybrid";
}
