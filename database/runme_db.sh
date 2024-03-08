#!/bin/bash

docker build -t assignmentpostgres .
docker run -d --name assignmentpostgrescontainer -p 5432:5432 assignmentpostgres
