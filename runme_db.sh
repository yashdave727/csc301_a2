#!/bin/bash

docker build -t assignmentpostgres .
docker run -d --name mypostgrescontainer -p 5432:5432 mypostgres
