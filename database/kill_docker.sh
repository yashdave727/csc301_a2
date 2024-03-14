#!/bin/bash

# Stop all running container
docker stop $(docker ps -q)

# Remove all stopped containers
docker rm $(docker ps -aq)
