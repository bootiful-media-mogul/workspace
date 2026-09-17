#!/usr/bin/env bash
set -euo pipefail
export BW_SESSION="$(bw unlock --raw)"
eval "$(./env.java)"