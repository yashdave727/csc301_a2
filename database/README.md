# Dockerized Database Setup

## Docker setup

To setup the database, a Dockerfile was created to create an instance of the database. The docker:

1. Chooses the latest version of PostgreSQL
2. Sets the database, user, and password of the database
3. Copies the file that initially sets up the database to the necessary location

## Script setup

The runme_db.sh script will run two commands:

1. Command to create the docker image (if image is created then no issue)
2. Command to run the docker image

## Run the script

To run the script, make sure that the script has the correct permissions to run. Do this by running:

```chmod +x runme_db.sh```

Verify permissions by running:

```ls -larth runme_db.sh```

Finally, run the command:
```./runme_db.sh```