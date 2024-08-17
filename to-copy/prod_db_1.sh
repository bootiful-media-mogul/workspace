#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}

export DB_URL="$(bw get item crunchydata-mogul-db-production | jq -r '.fields[] | select(.name == "url") | .value')"
export DB_USERNAME="$(bw get item crunchydata-mogul-db-production | jq -r '.fields[] | select(.name == "username") | .value')"
export DB_PASSWORD="$(bw get item crunchydata-mogul-db-production | jq -r '.fields[] | select(.name == "password") | .value')"
export DB_HOST="$(bw get item crunchydata-mogul-db-production | jq -r '.fields[] | select(.name == "host") | .value')"
export DB_DATABASE="$(bw get item crunchydata-mogul-db-production | jq -r '.fields[] | select(.name == "database") | .value')"

PGPASSWORD=$DB_PASSWORD psql -U $DB_USERNAME -h $DB_HOST $DB_DATABASE