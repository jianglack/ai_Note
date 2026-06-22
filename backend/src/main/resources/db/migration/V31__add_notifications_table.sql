-- V31: Notifications table for proactive intelligence
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL DEFAULT 'info',
    title VARCHAR(200) NOT NULL,
    content TEXT,
    source VARCHAR(50),
    related_id VARCHAR(36),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created ON notifications(created_at);

COMMENT ON TABLE notifications IS 'Proactive notifications from AI system';
COMMENT ON COLUMN notifications.type IS 'info|reminder|overdue|insight|suggestion';
COMMENT ON COLUMN notifications.source IS 'scheduler|analysis|manual';
