#!/usr/bin/env bash
export BW_SESSION=$(bw unlock --raw)
bw get item crunchydata-mogul-db-production   | jq -r '.notes'  | /bin/bash 