#!/bin/bash
NACOS_ADDR=${NACOS_ADDR:-localhost:8848}
until curl -s "$NACOS_ADDR/nacos/v1/console/health/readiness" > /dev/null 2>&1; do
  echo "Waiting for Nacos..."; sleep 2
done
for f in nacos-config/*.yml; do
  DATA_ID=$(basename "$f")
  echo "Publishing $DATA_ID..."
  curl -X POST "$NACOS_ADDR/nacos/v1/cs/configs" \
    -d "dataId=$DATA_ID&group=DEFAULT_GROUP&content=$(cat "$f")&type=yaml"
done
echo "Nacos config initialized."
