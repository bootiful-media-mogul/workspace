#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}

SQL_FILE=${SQL_FILE:-$HOME/Desktop/pg_dump.sql}
GKE_SQL_CREDS=$( bw get item mogul-gke-postgresql-db--production )
DB_PW=$( echo $GKE_SQL_CREDS |    jq '.. | objects | select(.name == "password" ) '  | jq -r .value )
DB_HOST=$( echo $GKE_SQL_CREDS |    jq '.. | objects | select(.name == "hostname" ) '  | jq -r .value )
DB_DB=$( echo $GKE_SQL_CREDS |    jq '.. | objects | select(.name == "database" ) '  | jq -r .value )
DB_USER=$( echo $GKE_SQL_CREDS |    jq '.. | objects | select(.name == "username" ) '  | jq -r .value )

#echo $DB_PW
#echo $DB_HOST
#echo $DB_USER
#echo $DB_DB

PGPASSWORD=$DB_PW pg_dump  --no-owner --no-privileges --if-exists -c --create \
  -U $DB_USER -h $DB_HOST $DB_DB >  $SQL_FILE