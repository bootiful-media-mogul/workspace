#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}

# DB_CREDS=`bw get item mogul-crunchydata-db-production `
DB_CREDS=$( bw get item 15ecc361-ac4b-42fb-98a1-b20701358338 )
USERNAME=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "username") | .value' )
PASSWORD=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "password") | .value' )
HOSTNAME=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "host") | .value' )
DB=$( echo $DB_CREDS | jq -r '.fields[] | select(.name == "database") | .value' )
echo $USERNAME
echo $DB
echo $PASSWORD
echo $HOSTNAME
psql postgres://${USERNAME}:${PASSWORD}@${HOSTNAME}:5432/${DB}
