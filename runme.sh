#!/bin/bash

# Absolute path of the runme.sh file
script_dir=$(dirname "$(readlink -f "$0")")

# Function to compile Java code
compile_code() {
    javac -d "$script_dir/compiled/OrderService" -cp "$script_dir/src/json-20231013.jar:$script_dir/src/postgresql-42.7.2.jar" "$script_dir/src/OrderService"/*.java
    javac -d "$script_dir/compiled/ProductService" -cp "$script_dir/src/json-20231013.jar:$script_dir/src/postgresql-42.7.2.jar" "$script_dir/src/ProductService"/*.java
    javac -d "$script_dir/compiled/UserService" -cp "$script_dir/src/json-20231013.jar:$script_dir/src/postgresql-42.7.2.jar" "$script_dir/src/UserService"/*.java

    # Copy all necessary dependencies
    cp "$script_dir/src/workload_parser.py" "$script_dir/compiled/"
    cp -r "$script_dir/src/ISCS/" "$script_dir/compiled/"
    cp "$script_dir/src/json-20231013.jar" "$script_dir/compiled/"
    cp "$script_dir/src/postgresql-42.7.2.jar" "$script_dir/compiled/"

    # Create databases
    echo '[]' > "$script_dir/compiled/UserService/user_backup.json"
    echo '[]' > "$script_dir/compiled/UserService/user_database.json"
    echo '[]' > "$script_dir/compiled/ProductService/product_backup.json"
    echo '[]' > "$script_dir/compiled/ProductService/product_database.json"

    if [ "$?" -eq 0 ]; then
        echo "Compilation successful."
    else
        echo "Compilation failed."
        exit 1
    fi
}

# Function to read in the config file
read_config() {
	# Read in the config file
	config=$(cat config.json)
	# echo "config: $config"
	# Parse the JSON without jq
	declare -g USER_PORT=$(echo "$config" | grep -oP '"user_port": \K[0-9]+')
	declare -g PRODUCT_PORT=$(echo "$config" | grep -oP '"product_port": \K[0-9]+')
	declare -g ORDER_PORT=$(echo "$config" | grep -oP '"order_port": \K[0-9]+')
	declare -g ISCS_IP=$(echo "$config" | grep -oP '"iscs_ip": "\K[^"]+')
	declare -g ISCS_PORT=$(echo "$config" | grep -oP '"iscs_port": \K[0-9]+')

	# Get the user ips as an array from the JSON without jq
	declare -g USER_IPS=$(echo "$config" | grep -oP '"user": \[\K[^]]+')
	# get the product ips as an array from the JSON without jq
	declare -g PRODUCT_IPS=$(echo "$config" | grep -oP '"product": \[\K[^]]+')

	echo "USER_IPS: $USER_IPS"
	echo "USER_PORT: $USER_PORT"
	echo "PRODUCT_IPS: $PRODUCT_IPS"
	echo "PRODUCT_PORT: $PRODUCT_PORT"
	echo "ORDER_PORT: $ORDER_PORT"
	echo "ISCS_PORT: $ISCS_PORT"
}

SYSTEM_DIR="~/csc301/csc301_a2"

# Function to start the ProductServices
product_services() {
	USERNAME=$1
	# For each product ip in the array, start a product service
	# first ssh to the product ip and then run the product service on the product port
	# The ssh username is provided as an environment variable
	for product_ip in $PRODUCT_IPS; do
		# Strip the quotes from the product_ip
		product_ip=$(echo $product_ip | tr -d '"')
		# Run the product service on the product_ip
		ssh -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa $USERNAME@$product_ip "cd $SYSTEM_DIR; ./runme.sh -p $PRODUCT_PORT"
		# Check that the command was successful
		if [ "$?" -eq 0 ]; then
			echo "Product service started at $USERNAME@$product_ip"
		else
			echo "Product service failed to start on $product_ip"
			# Remove the product service from the list of product services
		fi
	done
}
# Function to start the UserServices
user_services() {
	# For each user ip in the array, start a user service
	# first ssh to the user ip and then run the user service on the user port
	# The ssh username is provided as an environment variable
	USERNAME=$1
	for user_ip in $USER_IPS; do
		# Strip the quotes from the user_ip
		user_ip=$(echo $user_ip | tr -d '"')
		# Run the user service on the user_ip
		ssh -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa $USERNAME@$user_ip "cd $SYSTEM_DIR; ./runme.sh -u $USER_PORT"
		# Check that the command was successful
		if [ "$?" -eq 0 ]; then
			echo "User service started at $USERNAME@$user_ip"
		else
			echo "User service failed to start on $user_ip"
			# Remove the user service from the list of user services
		fi
	done
}

# Function to start the UserServices
start_us() {
	# run the user service with the user port
	echo "Starting user service on port $USER_PORT"
	java -cp "$script_dir/compiled/UserService:$script_dir/compiled/json-20231013.jar:$script_dir/compiled/postgresql-42.7.2.jar" UserService "$USER_PORT"
}

# Function to start the ProductService
start_ps() {
	# Run the product service with the product port
	echo "Starting product service on port $PRODUCT_PORT"
	java -cp "$script_dir/compiled/ProductService:$script_dir/compiled/json-20231013.jar:$script_dir/compiled/postgresql-42.7.2.jar" ProductService "$PRODUCT_PORT"
}

# Function to start the OrderService
start_os() {
	# Run the order service with the order port and the ISCS IP and port
	echo "Starting order service on port $ORDER_PORT"
	echo "ISCS: $ISCS_IP:$ISCS_PORT"
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
    python3 "$script_dir/compiled/ISCS/iscs.py" "$ISCS_PORT" "-d"
}

# read in config from config.json
read_config

# Main script
case "$1" in
    -c)
        compile_code
        ;;
    -u)
        start_us
        ;;
    -p)
        start_ps
        ;;
    -i)
        start_iscs
        ;;
    -o)
        start_os
        ;;
    -w)
        start_wg "$2"
        ;;
    -U)
	user_services "$2"
	;;
    -P)
	product_services "$2"
	;;
    *)
        echo "Usage: $0 {-c|-u|-p|-i|-o|-w workloadfile}"
        exit 1
        ;;
esac

exit 0
