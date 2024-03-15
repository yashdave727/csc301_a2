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

# Check for the docker port and redis port in the arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: ./start_docker.sh <docker_port> <redis_port>"
    exit 1
fi

# Check that the docker port is a number
if ! [[ $1 =~ ^[0-9]+$ ]]; then
    echo "Docker port must be a number"
    exit 1
fi

# Check that the redis port is a number
if ! [[ $2 =~ ^[0-9]+$ ]]; then
    echo "Redis port must be a number"
    exit 1
fi

# Check that the ports are not already in use
if [ "$(netstat -tuln | grep $1)" ]; then
    echo "Docker port is already in use"
    exit 1
fi

if [ "$(netstat -tuln | grep $2)" ]; then
    echo "Redis port is already in use"
    exit 1
fi

# Set the environment variables
DOCKER_PORT=$1
REDIS_PORT=$2



# Build postgres, Build redis
docker build -t assignmentpostgres ./postgres/
docker build -t assignmentredis ./redis/

# Run postgres
docker run -d --name assignmentpostgrescontainer -p $DOCKER_PORT:5432 assignmentpostgres
# Run redis
docker run -d --name assignmentrediscontainer -p $REDIS_PORT:6379 assignmentredis


