#!/bin/bash

# Absolute path of the directory containing this script
script_dir=$(dirname "$(readlink -f "$0")")

# Directory to store logs
logs_dir="$script_dir/logs"

# Create the logs directory if it doesn't exist
mkdir -p "$logs_dir"

# Local username and password for SSH
username="your_username"
password="your_password"

# Function to start the specified service
start_service() {
    local service="$1"
    local port=$(jq -r ".$service" "$script_dir/config.json" | jq -r '.port')
    local docker_ip=$(jq -r '.docker' "$script_dir/config.json")
    local db_port=$(jq -r '.db_port' "$script_dir/config.json")
    local rd_port=$(jq -r '.rd_port' "$script_dir/config.json")
    local ips=($(jq -r ".$service[]" "$script_dir/config.json"))

    for ip in "${ips[@]}"; do
        service_log="$logs_dir/${service}_${ip}_${port}.log"
        echo "Starting $service service on $ip:$port..."
        sshpass -p "$password" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$username@$ip" "bash $script_dir/runme.sh -$service $port $docker_ip $db_port $rd_port" >> "$service_log" 2>&1 &
    done
}

# Main script
case "$1" in
    -c)
        bash "$script_dir/runme.sh" -c
        ;;
    -u)
        start_service "user"
        ;;
    -p)
        start_service "product"
        ;;
    -o)
        start_service "order"
        ;;
    *)
        echo "Usage: $0 { -c | -u | -p | -o }"
        exit 1
        ;;
esac

exit 0
