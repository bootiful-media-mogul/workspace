#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}

DB_CREDS=`bw get item mogul-crunchydata-db-production `

USERNAME=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "username") | .value' )
PASSWORD=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "password") | .value' )
HOSTNAME=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "host") | .value' )
DB=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "database") | .value' )

echo $USERNAME
echo $DB
echo $PASSWORD
echo $HOSTNAME

# PGPASSWORD=$PASSWORD psql "sslmode=disable dbname=$DB user=$USERNAME hostaddr=postgres://postgres:${HOSTNAME}"
psql postgres://${USERNAME}:${PASSWORD}@${HOSTNAME}:5432/${DB}
