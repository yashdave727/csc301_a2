#!/bin/bash

# Build postgres, Build redis
docker build -t assignmentpostgres .
docker build -t assignmentredis ./redis/

# Run postgres
docker run -d --name assignmentpostgrescontainer -p 5432:5432 assignmentpostgres
# Run redis
docker run -d --name assignmentrediscontainer assignmentredis


