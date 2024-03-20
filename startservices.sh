#!/bin/bash

# Absolute path of the directory containing this script
script_dir=$(dirname "$(readlink -f "$0")")

# Path to the JSON file
json_file="$script_dir/config.json"

# Read the JSON file
json_data=$(<"$json_file")

# Extract IPs and ports
docker_ip=$(grep -o '"docker": "[^"]*' <<< "$json_data" | grep -o '[^"]*$')
db_port=$(grep -o '"db_port": [0-9]*' <<< "$json_data" | grep -o '[0-9]*')
rd_port=$(grep -o '"rd_port": [0-9]*' <<< "$json_data" | grep -o '[0-9]*')
user_ips=$(echo "$json_data" | grep -oP '(?<="user": \[)[^]]*(?=])' | tr -d '[" ]' | tr ',' '\n')
user_port=$(grep -o '"user_port": [0-9]*' <<< "$json_data" | grep -o '[0-9]*')
product_ips=$(echo "$json_data" | grep -oP '(?<="product": \[)[^]]*(?=])' | tr -d '[" ]' | tr ',' '\n')
product_port=$(grep -o '"product_port": [0-9]*' <<< "$json_data" | grep -o '[0-9]*')
order_ips=$(echo "$json_data" | grep -oP '(?<="order": \[)[^]]*(?=])' | tr -d '[" ]' | tr ',' '\n')
order_port=$(grep -o '"order_port": [0-9]*' <<< "$json_data" | grep -o '[0-9]*')

# Directory to store logs
logs_dir="$script_dir/logs"

# Create the logs directory if it doesn't exist
mkdir -p "$logs_dir"

# Local username and password for SSH
username="deyjai"

# Function to start the specified service
start_service() {
    local command="$1"
    local service="$2"

    if [[ "$service" == "user" ]]; then
        local ips="$user_ips"
        local port="$user_port"
    elif [[ "$service" == "product" ]]; then
        local ips="$product_ips"
        local port="$product_port"
    elif [[ "$service" == "order" ]]; then
        local ips="$order_ips"
        local port="$order_port"
    else
        exit 1
    fi

    echo "command:$command"
    echo "service:$service"
    echo "port:$port"
    echo "docker_ip:$docker_ip"
    echo "db_port:$db_port"
    echo "rd_port:$rd_port"
    while IFS= read -r ip; do
        service_log="$logs_dir/${service}_${ip}_${port}.log"
        echo "Starting $service service on $ip:$port..."
        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$username@$ip" "bash $script_dir/runme.sh $command $port $docker_ip $db_port $rd_port" >> "$service_log" 2>&1 &
    done <<< "$ips"
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
