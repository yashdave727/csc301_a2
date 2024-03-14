#!/bin/bash

# Check if containers are running
if [ $(docker ps -q | wc -l) -eq 0 ]; then
    echo "No containers are running"
    exit 0
fi

# Stop all running container
docker stop $(docker ps -q)

# Remove all stopped containers
docker rm $(docker ps -aq)
