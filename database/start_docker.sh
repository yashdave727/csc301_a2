#!/bin/bash

# First, check that containers are not running
# If they are, tell user to run stop_docker.sh
if [ "$(docker ps -q -f name=assignmentpostgrescontainer)" ]; then
    echo "Containers are already running. Please run kill_docker.sh first."
    exit 1
fi


# Build postgres, Build redis
docker build -t assignmentpostgres .
docker build -t assignmentredis ./redis/

# Run postgres
docker run -d --name assignmentpostgrescontainer -p 5432:5432 assignmentpostgres
# Run redis
docker run -d --name assignmentrediscontainer assignmentredis


