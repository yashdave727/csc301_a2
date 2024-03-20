#!/bin/bash

# Absolute path of the directory containing the script
script_dir=$(dirname "$(readlink -f "$0")")

# Directory containing logs
logs_dir="$script_dir/logs"

# Function to kill services
kill_services() {
    local service="$1"
    local pattern="${service}_*"

    # Find all log files matching the service pattern
    log_files=$(find "$logs_dir" -maxdepth 1 -name "$pattern")

    # Loop through log files and extract PIDs to kill
    for log_file in $log_files; do
        pid=$(grep -oP '\b[0-9]+(?=\.[lL][oO][gG])' <<< "$log_file")
        if [ -n "$pid" ]; then
            echo "Killing service with PID: $pid"
            kill "$pid"
        fi
    done
}

# Main script
case "$1" in
    -u)
        kill_services "user"
        ;;
    -p)
        kill_services "product"
        ;;
    -o)
        kill_services "order"
        ;;
    *)
        echo "Usage: $0 { -u | -p | -o }"
        exit 1
        ;;
esac

exit 0
