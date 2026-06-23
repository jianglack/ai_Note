ALTER TABLE folders DROP CONSTRAINT IF EXISTS folders_parent_id_fkey;
ALTER TABLE folders
    ADD CONSTRAINT folders_parent_id_fkey
    FOREIGN KEY (parent_id) REFERENCES folders(id) ON DELETE SET NULL;

ALTER TABLE notes DROP CONSTRAINT IF EXISTS notes_folder_id_fkey;
ALTER TABLE notes
    ADD CONSTRAINT notes_folder_id_fkey
    FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_note_tags_tag_id ON note_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_notes_user_deleted_updated
    ON notes(user_id, deleted_at, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_notes_user_folder_deleted
    ON notes(user_id, folder_id, deleted_at);
