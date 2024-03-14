#!/bin/bash

# Check that we're running on the school VM
# We can check the ip using curl ipinfo.io/ip
# It must equal 142.1.44.57
if [ $(curl -s ipinfo.io/ip) != "142.1.44.57" ]; then
    echo "You are not running on the school VM"
    exit 1
fi

# First, check that containers are not running
# If they are, tell user to run stop_docker.sh
if [ "$(docker ps -q -f name=assignmentpostgrescontainer)" ]; then
    echo "Containers are already running. Please run kill_docker.sh first."
    exit 1
fi


# Build postgres, Build redis
docker build -t assignmentpostgres ./postgres/
docker build -t assignmentredis ./redis/

# Run postgres
docker run -d --name assignmentpostgrescontainer -p 5432:5432 assignmentpostgres
# Run redis
docker run -d --name assignmentrediscontainer assignmentredis


