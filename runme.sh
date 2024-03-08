#!/bin/bash

# Absolute path of the runme.sh file
script_dir=$(dirname "$(readlink -f "$0")")

# Function to compile Java code
compile_code() {
    javac -d "$script_dir/compiled/OrderService" -cp "$script_dir/src/json-20231013.jar" "$script_dir/src/OrderService"/*.java
    javac -d "$script_dir/compiled/ProductService" -cp "$script_dir/src/json-20231013.jar" "$script_dir/src/ProductService"/*.java
    javac -d "$script_dir/compiled/UserService" -cp "$script_dir/src/json-20231013.jar" "$script_dir/src/UserService"/*.java

    # Copy all necessary dependencies
    cp "$script_dir/src/workload_parser.py" "$script_dir/compiled/"
    cp "$script_dir/src/json-20231013.jar" "$script_dir/compiled/"

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

# Function to start the UserService
start_us() {
    # Run the Java program with the script directory as an argument
    java -cp "$script_dir/compiled/UserService:$script_dir/compiled/json-20231013.jar" UserService "$script_dir"
}

# Function to start the ProductService
start_ps() {
    # Run the Java program with the script directory as an argument
    java -cp "$script_dir/compiled/ProductService:$script_dir/compiled/json-20231013.jar" ProductService "$script_dir"
}

# Function to start the OrderService
start_os() {
    # Run the Java program with the script directory as an argument
    java -cp "$script_dir/compiled/OrderService:$script_dir/compiled/json-20231013.jar" OrderService "$script_dir"
}

# Function to start the WorkloadParser
start_wg() {
    if [ -z "$1" ]; then
        echo "Error: Workload file not provided."
        exit 1
    fi

    python3 "$script_dir/compiled/workload_parser.py" "$1"
}

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
    *)
        echo "Usage: $0 {-c|-u|-p|-i|-o|-w workloadfile}"
        exit 1
        ;;
esac

exit 0
