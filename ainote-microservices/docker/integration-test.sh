#!/bin/bash
# End-to-end integration test script
# Usage: ./integration-test.sh [gateway_url]

GATEWAY=${1:-http://localhost:8080}
PASS=0
FAIL=0

check() {
  local desc="$1"
  local cmd="$2"
  local expected="$3"

  result=$(eval "$cmd" 2>/dev/null)
  if echo "$result" | grep -q "$expected"; then
    echo "[PASS] $desc"
    ((PASS++))
  else
    echo "[FAIL] $desc"
    echo "       Expected: $expected"
    echo "       Got: $result"
    ((FAIL++))
  fi
}

echo "=== AiNote Integration Tests ==="
echo "Gateway: $GATEWAY"
echo ""

# 1. Nacos health
check "Nacos is running" \
  "curl -s http://localhost:8848/nacos/v1/console/health/readiness" \
  "ok"

# 2. Register user
check "POST /api/auth/register" \
  "curl -s -X POST $GATEWAY/api/auth/register -H 'Content-Type: application/json' -d '{\"username\":\"testuser\",\"email\":\"test@test.com\",\"password\":\"Test1234!\"}'" \
  "testuser"

# 3. Login
TOKEN=$(curl -s -X POST $GATEWAY/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","password":"Test1234!"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then
  echo "[PASS] POST /api/auth/login - got JWT"
  ((PASS++))
else
  echo "[FAIL] POST /api/auth/login - no JWT returned"
  ((FAIL++))
fi

AUTH="Authorization: Bearer $TOKEN"

# 4. Create note
check "POST /api/notes" \
  "curl -s -X POST $GATEWAY/api/notes -H '$AUTH' -H 'Content-Type: application/json' -d '{\"title\":\"Test Note\",\"content\":\"Hello microservices\"}'" \
  "Test Note"

# 5. List notes
check "GET /api/notes" \
  "curl -s $GATEWAY/api/notes -H '$AUTH'" \
  "Test Note"

# 6. Create schedule
check "POST /api/schedules" \
  "curl -s -X POST $GATEWAY/api/schedules -H '$AUTH' -H 'Content-Type: application/json' -d '{\"title\":\"Test Schedule\",\"startTime\":\"2026-12-01T10:00:00\",\"endTime\":\"2026-12-01T11:00:00\"}'" \
  "Test Schedule"

# 7. Elasticsearch
check "Elasticsearch is running" \
  "curl -s http://localhost:9200" \
  "lucene"

# 8. Debezium connectors
check "Debezium connectors registered" \
  "curl -s http://localhost:8083/connectors" \
  "ainote-notes-connector"

# 9. Hybrid search (may need a few seconds for CDC)
sleep 3
check "POST /api/search/hybrid" \
  "curl -s -X POST $GATEWAY/api/search/hybrid -H '$AUTH' -H 'Content-Type: application/json' -d '{\"query\":\"Test\",\"limit\":10}'" \
  "noteId"

# 10. AI chat
check "POST /api/ai/chat" \
  "curl -s -X POST $GATEWAY/api/ai/chat -H '$AUTH' -H 'Content-Type: application/json' -d '{\"message\":\"你好\"}'" \
  "reply"

# 11. Graph
check "GET /api/graph" \
  "curl -s $GATEWAY/api/graph -H '$AUTH'" \
  "nodes"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
