# Use Latest Version
FROM postgres:latest

# Set ENV Variables
ENV POSTGRES_DB=assignmentdb
ENV POSTGRES_USER=assignmentuser
ENV POSTGRES_PASSWORD=assignmentpassword

# Copy postgresql.conf to container
COPY ./postgresql.conf /etc/postgresql/postgresql.conf

# Expose Port
EXPOSE 5432
