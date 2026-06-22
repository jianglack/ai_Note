#!/bin/bash
# Data migration script: monolith DB → microservice DBs
# Usage: ./migrate-data.sh [source_host] [target_host]

SOURCE_HOST=${1:-localhost}
TARGET_HOST=${2:-localhost}
DB_USER="ainote"

echo "=== AiNote Data Migration ==="
echo "Source: $SOURCE_HOST, Target: $TARGET_HOST"

# Step 1: Migrate users → ainote_auth
echo "[1/4] Migrating users to ainote_auth..."
pg_dump -h $SOURCE_HOST -U $DB_USER -d ainote -t users --data-only | \
  psql -h $TARGET_HOST -U $DB_USER -d ainote_auth
echo "Users migration complete."

# Step 2: Migrate notes, folders, tags → ainote_notes
echo "[2/4] Migrating notes data to ainote_notes..."
pg_dump -h $SOURCE_HOST -U $DB_USER -d ainote \
  -t notes -t folders -t tags -t note_tags -t annotations -t note_versions \
  --data-only | \
  psql -h $TARGET_HOST -U $DB_USER -d ainote_notes
echo "Notes migration complete."

# Step 3: Migrate schedules → ainote_schedules
echo "[3/4] Migrating schedules to ainote_schedules..."
pg_dump -h $SOURCE_HOST -U $DB_USER -d ainote -t schedules --data-only | \
  psql -h $TARGET_HOST -U $DB_USER -d ainote_schedules
echo "Schedules migration complete."

# Step 4: Trigger search reindex
echo "[4/4] Triggering search reindex..."
GATEWAY_URL="http://${TARGET_HOST}:8080"
curl -s -X POST "$GATEWAY_URL/api/search/reindex" \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" && echo ""
echo "Reindex triggered."

echo "=== Migration Complete ==="
echo ""
echo "Verify with:"
echo "  psql -h $TARGET_HOST -U $DB_USER -d ainote_auth -c 'SELECT count(*) FROM users;'"
echo "  psql -h $TARGET_HOST -U $DB_USER -d ainote_notes -c 'SELECT count(*) FROM notes;'"
echo "  psql -h $TARGET_HOST -U $DB_USER -d ainote_schedules -c 'SELECT count(*) FROM schedules;'"
