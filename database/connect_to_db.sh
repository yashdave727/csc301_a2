#!/bin/bash

# Read in the port from the command line
port=$1

# if no port is given, exit
if [ -z $port ]; then
    echo "Please provide a port number"
    exit 1
fi

echo "The password is assignmentpassword"
psql -h 142.1.44.57 -p $port -U assignmentuser -d assignmentdb
