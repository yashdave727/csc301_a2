#!/bin/bash

# Absolute path of the runme.sh file
script_dir=$(dirname "$(readlink -f "$0")")

# Function to compile Java code
compile_code() {

    # Copy all necessary dependencies
    cp "$script_dir/src/workload_parser.py" "$script_dir/compiled/"
    cp -r "$script_dir/src/ISCS/" "$script_dir/compiled/"
    cp "$script_dir/src/json-20231013.jar" "$script_dir/compiled/"
    cp "$script_dir/src/postgresql-42.7.2.jar" "$script_dir/compiled/"
    cp "$script_dir/src/jedis-5.2.0-beta1.jar" "$script_dir/compiled/"
    
    # Compile the Java code
    javac -d "$script_dir/compiled/OrderService" -cp "$script_dir/src/jedis-5.2.0-beta1.jar:$script_dir/src/json-20231013.jar:$script_dir/src/postgresql-42.7.2.jar" "$script_dir/src/OrderService"/*.java
    javac -d "$script_dir/compiled/ProductService" -cp "$script_dir/src/jedis-5.2.0-beta1.jar:$script_dir/src/json-20231013.jar:$script_dir/src/postgresql-42.7.2.jar" "$script_dir/src/ProductService"/*.java
    javac -d "$script_dir/compiled/UserService" -cp "$script_dir/src/jedis-5.2.0-beta1.jar:$script_dir/src/json-20231013.jar:$script_dir/src/postgresql-42.7.2.jar" "$script_dir/src/UserService"/*.java

    if [ "$?" -eq 0 ]; then
        echo "Compilation successful."
    else
        echo "Compilation failed."
        exit 1
    fi
}

# Function to start the UserServices
start_us() {
	# run the user service with the user port
	USER_PORT=$1
	DOCKER_IP=$2
	DB_PORT=$3
	RD_PORT=$4
	java -cp "$script_dir/compiled/UserService:$script_dir/compiled/json-20231013.jar:$script_dir/compiled/postgresql-42.7.2.jar" UserService "$USER_PORT" "$DOCKER_IP" "$DB_PORT" "$RD_PORT"
}

# Function to start the ProductService
start_ps() {
	# Run the product service with the product port
	PRODUCT_PORT=$1
	DOCKER_IP=$2
	DB_PORT=$3
	RD_PORT=$4
	java -cp "$script_dir/compiled/ProductService:$script_dir/compiled/json-20231013.jar:$script_dir/compiled/postgresql-42.7.2.jar" ProductService "$PRODUCT_PORT" "$DOCKER_IP" "$DB_PORT" "$RD_PORT"
}

# Function to start the OrderService
start_os() {
	# Run the order service with the order port and the ISCS IP and port
	ORDER_PORT=$1
	ISCS_IP=$2
	ISCS_PORT=$3
	echo "Starting OrderService with port $ORDER_PORT and ISCS IP $ISCS_IP and port $ISCS_PORT"
	java -cp "$script_dir/compiled/OrderService:$script_dir/compiled/json-20231013.jar:$script_dir/compiled/postgresql-42.7.2.jar" OrderService "$ORDER_PORT" "$ISCS_IP:$ISCS_PORT"
}

# Function to start the WorkloadParser
start_wg() {
    if [ -z "$1" ]; then
        echo "Error: Workload file not provided."
        exit 1
    fi

    python3 "$script_dir/compiled/workload_parser.py" "$1"
}

# Function to start the ISCS
start_iscs() {
	ISCS_PORT=$1
	if [ -z "$ISCS_PORT" ]; then
		python3 "$script_dir/compiled/ISCS/iscs.py"
	fi
	python3 "$script_dir/compiled/ISCS/iscs.py" "$ISCS_PORT"
}

# Function to start the database
start_db() {
	bash "$script_dir/database/runme_db.sh"
}

# Main script
case "$1" in
    -c)
        compile_code
        ;;
    -u)
        start_us "$2" "$3" "$4" "$5"
        ;;
    -p)
        start_ps "$2" "$3" "$4" "$5"
        ;;
    -i)
	start_iscs "$2"
        ;;
    -o)
        start_os "$2" "$3" "$4"
        ;;
    -w)
        start_wg "$2"
	;;
    -d)
	start_db
	;;
    *)
        echo "Usage: $0 { -c | -d | -u port | -p port | -i port | -o port | -w workloadfile }"
        exit 1
        ;;
esac

exit 0
