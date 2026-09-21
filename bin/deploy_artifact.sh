#!/usr/bin/env bash
./mvnw deploy -Dwagon.repository.github.username="${GH_USER}" -Dwagon.repository.github.password="${GH_TOKEN}"
