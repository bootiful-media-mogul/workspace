#!/usr/bin/env bash

docker compose  -f  $(dirname $0)/compose.yaml up -d
