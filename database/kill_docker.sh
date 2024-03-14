#!/bin/bash

# Check that we're running on the school VM
# We can check the ip using curl ipinfo.io/ip
# It must equal 142.1.44.57
if [ $(curl -s ipinfo.io/ip) != "142.1.44.57" ]; then
    echo "You are not running on the school VM"
    exit 1
fi

# Check if containers are running
if [ $(docker ps -q | wc -l) -eq 0 ]; then
    echo "No containers are running"
    exit 0
fi

# Stop all running container
docker stop $(docker ps -q)

# Remove all stopped containers
docker rm $(docker ps -aq)
