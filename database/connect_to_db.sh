#!/bin/bash

# Read in the port from the command line
port=$1

echo "The password is assignmentpassword"
psql -h 142.1.44.57 -p $port -U assignmentuser -d assignmentdb
