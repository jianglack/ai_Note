CREATE TABLE IF NOT EXISTS schedule_notes (
    schedule_id TEXT NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    PRIMARY KEY (schedule_id, note_id)
);

CREATE INDEX IF NOT EXISTS idx_schedule_notes_note_id ON schedule_notes(note_id);
