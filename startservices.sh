#!/bin/bash

# Absolute path of the directory containing this script
script_dir=$(dirname "$(readlink -f "$0")")

# Directory to store logs
logs_dir="$script_dir/logs"

# Create the logs directory if it doesn't exist
mkdir -p "$logs_dir"

# Local username and password for SSH
username="your_username"

# Function to start the specified service
start_service() {
    local command="$1"
    local service="$2"
    local port=$(grep -oP "\"$service\":\s*\K\d+" "$script_dir/config.json")
    local docker_ip=$(grep -oP "\"docker\":\s*\"\K[^\" ]+" "$script_dir/config.json")
    local db_port=$(grep -oP "\"db_port\":\s*\K\d+" "$script_dir/config.json")
    local rd_port=$(grep -oP "\"rd_port\":\s*\K\d+" "$script_dir/config.json")
    local ips=($(grep -oP "\"$service\":\s*\[\s*\K[^\]]+" "$script_dir/config.json" | grep -oP "\d+\.\d+\.\d+\.\d+"))

    for ip in "${ips[@]}"; do
        service_log="$logs_dir/${service}_${ip}_${port}.log"
        echo "Starting $service service on $ip:$port..."
        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$username@$ip" "bash $script_dir/runme.sh $command $port $docker_ip $db_port $rd_port" >> "$service_log" 2>&1 &
    done
}

# Main script
case "$1" in
    -c)
        bash "$script_dir/runme.sh" -c
        ;;
    -u)
        start_service "-u" "user"
        ;;
    -p)
        start_service "-p" "product"
        ;;
    -o)
        start_service "-o" "order"
        ;;
    *)
        echo "Usage: $0 { -c | -u | -p | -o }"
        exit 1
        ;;
esac

exit 0
