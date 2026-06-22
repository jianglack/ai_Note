#!/bin/bash
DEBEZIUM_ADDR=${1:-localhost:8083}
echo "Waiting for Debezium Connect..."
until curl -s "$DEBEZIUM_ADDR/connectors" > /dev/null 2>&1; do sleep 3; done
echo "Registering notes connector..."
curl -X POST "$DEBEZIUM_ADDR/connectors" -H "Content-Type: application/json" -d @"$(dirname "$0")/notes-connector.json"
echo ""
echo "Registering schedules connector..."
curl -X POST "$DEBEZIUM_ADDR/connectors" -H "Content-Type: application/json" -d @"$(dirname "$0")/schedules-connector.json"
echo ""
echo "Done. Active connectors:"
curl -s "$DEBEZIUM_ADDR/connectors"
