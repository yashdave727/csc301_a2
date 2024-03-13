#!/bin/bash

docker build -t assignmentpostgres .
docker run -d --name assignmentpostgrescontainer12 -p 142.1.46.61:5434:5432 assignmentpostgres
