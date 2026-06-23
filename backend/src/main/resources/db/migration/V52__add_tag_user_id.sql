ALTER TABLE tags ADD COLUMN IF NOT EXISTS user_id TEXT;

ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_name_key;

CREATE TEMP TABLE tag_owner_map ON COMMIT DROP AS
SELECT old_tag_id,
       tag_name,
       user_id,
       CASE
           WHEN owner_rank = 1 THEN old_tag_id
           ELSE 'tag-' || md5(old_tag_id || ':' || user_id || ':V52')
       END AS new_tag_id
FROM (
    SELECT DISTINCT
           nt.tag_id AS old_tag_id,
           t.name AS tag_name,
           n.user_id AS user_id,
           ROW_NUMBER() OVER (PARTITION BY nt.tag_id ORDER BY n.user_id) AS owner_rank
    FROM note_tags nt
    JOIN notes n ON n.id = nt.note_id
    JOIN tags t ON t.id = nt.tag_id
) owners;

INSERT INTO tags (id, name, user_id)
SELECT new_tag_id, tag_name, user_id
FROM tag_owner_map
WHERE new_tag_id <> old_tag_id;

UPDATE tags t
SET user_id = m.user_id
FROM tag_owner_map m
WHERE t.id = m.old_tag_id
  AND m.new_tag_id = m.old_tag_id;

UPDATE note_tags nt
SET tag_id = m.new_tag_id
FROM notes n
JOIN tag_owner_map m ON m.user_id = n.user_id
WHERE n.id = nt.note_id
  AND m.old_tag_id = nt.tag_id
  AND m.new_tag_id <> nt.tag_id;

DELETE FROM tags
WHERE user_id IS NULL;

ALTER TABLE tags ALTER COLUMN user_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_tags_user_id'
    ) THEN
        ALTER TABLE tags
            ADD CONSTRAINT fk_tags_user_id
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tags_user_id ON tags(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tags_user_name ON tags(user_id, name);
