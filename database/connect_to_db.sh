#!/bin/bash
echo "The password is asignmentpassword"
psql -h 142.1.44.57 -p 5432 -U assignmentuser -d assignmentdb
