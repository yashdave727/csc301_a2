'''
iscs.py

ISCS: Inter-Service Communication System

This module works as a load balancer and a request forwarder for the
User, Product and Order systems.

Usage:
./iscs.py [port] [-d]
'''

import sys
import os
import json
from flask import Flask, redirect, jsonify

# Global variables (default values)
ORDER_PORT = 7998
ISCS_PORT = 7999
USER_PORT = 8000
PRODUCT_PORT = 9000

USER_IPS = ["localhost"]
PRODUCT_IPS = ["localhost"]

app = Flask(__name__)

# Redirect routes
@app.route('/<endpoint>', methods=['POST'])
def forward_request(endpoint):
    """Forward the request to the appropriate service.
    possible endpoints include:
    /user
    /product
    """
    # Get the next IP address for the given service
    next_ip = get_next_service_ip(endpoint)

    # Debugging info
    if app.debug and next_ip:
        print(f"Forwarding request to {next_ip}/{endpoint}", file=sys.stderr)
    elif not next_ip and app.debug:
        print(f"Invalid endpoint: {endpoint}", file=sys.stderr)

    if next_ip:
        # Forward the request to the next service
        return redirect(f"{next_ip}/{endpoint}", code=307)
    # Invalid endpoint
    return jsonify({"error": "Invalid endpoint"}), 400

@app.route('/<endpoint>/<_id>', methods=['GET'])
def forward_request_with_id(endpoint, _id):
    """Forward the request to the appropriate service.
    possible endpoints include:
    /user/<id>
    /product/<id>
    """
    # Get the next IP address for the given service
    next_ip = get_next_service_ip(endpoint)

    # Debugging info
    if app.debug and next_ip:
        print(f"Forwarding request to {next_ip}/{endpoint}/{_id}", file=sys.stderr)
    elif not next_ip and app.debug:
        print(f"Invalid endpoint: {endpoint}", file=sys.stderr)

    if next_ip:
        # Forward the request to the next service
        return redirect(f"{next_ip}/{endpoint}/{_id}", code=307)
    # Invalid endpoint
    return jsonify({"error": "Invalid endpoint"}), 400

# Error handling (defined in a for loop to avoid repetition)

# Error codes using the 4xx and 5xx ranges
error_codes = [400, 401, 403, 404, 405, 500, 501, 502, 503, 504]

for _code in error_codes:
    @app.errorhandler(_code)
    def error_handler(error, code=_code):
        """Error handler for the given error code."""
        print("Error", error, file=sys.stderr)
        return jsonify({"error": f"Error {code}"}), code

# Helper functions

def get_next_service_ip(endpoint_name):
    """Get the IP address with port of the service with the given endpoint name.
    """
    # Get the next IP address for the given service
    if endpoint_name == "user":
        ip = USER_IPS.pop(0)
        USER_IPS.append(ip)
        return f"http://{ip}:{USER_PORT}"
    if endpoint_name == "product":
        ip = PRODUCT_IPS.pop(0)
        PRODUCT_IPS.append(ip)
        return f"http://{ip}:{PRODUCT_PORT}"
    return None

def read_ips():
    """Read in the IP addresses of the user and product services.
    These IP addresses are read in from a file called "ips.json" which is located in the same
    directory as this file. The file should be in the following format:
    {
        "user": ["ip1", "ip2", ...],
        "product": ["ip1", "ip2", ...]
        "user_port": port_number,
        "product_port": port_number
    }
    """
    global USER_IPS, PRODUCT_IPS, USER_PORT, PRODUCT_PORT

    # change directory to the current file's directory (to read in the ips.json file)
    os.chdir(os.path.dirname(os.path.abspath(__file__)))


    with open("ips.json", "r", encoding="ASCII") as file:
        data = json.load(file)
        USER_IPS = data["user"]
        PRODUCT_IPS = data["product"]
        USER_PORT = data["user_port"]
        PRODUCT_PORT = data["product_port"]

    # if debug is set, print out the number of user and product services and their IP addresses
    if app.debug:
        print(f"User Services: (total: {len(USER_IPS)})", file=sys.stderr)
        for ip in USER_IPS:
            print(f"  {ip}:{USER_PORT}", file=sys.stderr)
        print(f"Product Services: (total: {len(PRODUCT_IPS)})", file=sys.stderr)
        for ip in PRODUCT_IPS:
            print(f"  {ip}:{PRODUCT_PORT}", file=sys.stderr)

def main():
    """Main function for the ISCS module. This function reads in the command line arguments,
    prints out the configuration, and starts listening for requests.
    """
    # Read in the command line arguments
    global ISCS_PORT
    match (len(sys.argv)):
        case 3:
            # Set the iscs port
            try:
                ISCS_PORT = int(sys.argv[1])
            except ValueError:
                print("Invalid ISCS port value", file=sys.stderr)
                sys.exit(1)
            # Set the debug flag
            if sys.argv[2] == "-d":
                app.debug = True
            else:
                print(f"Invalid option {sys.argv[2]}", file=sys.stderr)
                sys.exit(1)
        case 2:
            # If the second argument is the debug flag
            if sys.argv[1] == "-d":
                app.debug = True
            else:
                try:
                    ISCS_PORT = int(sys.argv[1])
                except ValueError:
                    print(f"Invalid argument {sys.argv[1]}", file=sys.stderr)
                    sys.exit(1)
        case 1:
            # Use default
            print("Using default ISCS port", file=sys.stderr)
            pass
        case _:
            # Invalid number of arguments
            print("Usage: ./iscs.py [order_service_port] [-d]", file=sys.stderr)
            sys.exit(1)

    # Read in the IP addresses of the user and product services
    read_ips()

    # Print out the configuration if the app is running and the debug flag is set
    if app.debug:
        print("ISCS Configuration:", file=sys.stderr)
        print(f"  ISCS Port: {ISCS_PORT}", file=sys.stderr)
        # Print out the user service IPs and port
        print("  User Services:", file=sys.stderr)
        for ip in USER_IPS:
            print(f"    {ip}:{USER_PORT}", file=sys.stderr)

        # Print out the product service IPs and port
        print("  Product Services:", file=sys.stderr)
        for ip in PRODUCT_IPS:
            print(f"    {ip}:{PRODUCT_PORT}", file=sys.stderr)

    print("Starting ISCS...", file=sys.stderr)

    # Start listening for requests with a multi-threaded server
    app.run(port=ISCS_PORT, threaded=True)

if __name__ == "__main__":
    main()
