package com.ainote.app.model;

import java.util.List;

public class ChatHistoryPage {
    private List<ChatHistoryItem> items;
    private String nextCursor;
    private boolean hasMore;

    public ChatHistoryPage() {
    }

    public ChatHistoryPage(List<ChatHistoryItem> items, String nextCursor, boolean hasMore) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<ChatHistoryItem> getItems() {
        return items;
    }

    public void setItems(List<ChatHistoryItem> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
