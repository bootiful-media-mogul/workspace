#!/usr/bin/env bash

export SQL_FILE=$HOME/Desktop/for-local.sql
ls -la $SQL_FILE || /bin/bash ./backup_prod_db.sh
cat $SQL_FILE | PGPASSWORD=mogul psql -U mogul -h localhost mogul
