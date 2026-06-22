-- V27: Notifications system for proactive AI

CREATE TABLE IF NOT EXISTS ai_notifications (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT,
    source_type TEXT,
    source_id TEXT,
    read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_notifications_user_unread ON ai_notifications(user_id) WHERE read = FALSE;
