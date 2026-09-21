#!/usr/bin/env bash
set -euo pipefail
SETTINGS="${MOGUL_HOME}/workspace/bin/settings.xml"
./mvnw -s "$SETTINGS" deploy "$@"
