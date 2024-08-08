#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}
echo "got the BW_SESSION: ${BW_SESSION}"
bw get item crunchydata-mogul-db-production   | jq -r '.notes'  | /bin/bash 