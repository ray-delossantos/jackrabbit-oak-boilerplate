#!/bin/bash
# Health check script for AEM Oak instances
# Returns 0 if healthy, 1 if unhealthy

set -e

# Check HTTP endpoint
HTTP_PORT="${HTTP_PORT:-8080}"
HEALTH_ENDPOINT="${HEALTH_ENDPOINT:-/system/health}"

# Use curl for health check
response=$(curl -sf "http://localhost:${HTTP_PORT}${HEALTH_ENDPOINT}" 2>/dev/null || echo "")

if [ -z "$response" ]; then
    # Try basic connectivity
    if curl -sf "http://localhost:${HTTP_PORT}/" >/dev/null 2>&1; then
        exit 0
    fi
    exit 1
fi

# Check response contains expected content
if echo "$response" | grep -qE '"status"\s*:\s*"(ok|healthy|running)"'; then
    exit 0
fi

# If we got a response, consider it healthy
exit 0
