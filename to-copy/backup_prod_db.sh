#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}
echo "got the BW_SESSION: ${BW_SESSION}"
export SQL_FILE=${SQL_FILE:-$HOME/Desktop/output.sql}
bw get item crunchydata-mogul-db-production | jq -r '.notes' | /bin/bash
ls -la $SQL_FILE || echo "there's no .SQL file backup to be found."