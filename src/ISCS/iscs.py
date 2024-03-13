#!/usr/bin/python3
'''
iscs.py

ISCS: Inter-Service Communication System

This module works as a load balancer and a request forwarder for the
User, Product and Order systems.

Usage:
./iscs.py [-h] [-d] [port]
'''

import sys
import logging
import os
import json
from flask import Flask, redirect, jsonify
import argparse

# Command line arguments
DESCRIPTION = "ISCS: Inter-Service Communication System\n\n\
This module works as a load balancer and a request forwarder for the User, Product and Order systems.\n\
This module requires the ips.json file to be in the same directory as this file. The ips.json file should be in the following format:\n\
{\n\
    \"user\": [\"ip1\", \"ip2\", ...],\n\
    \"product\": [\"ip1\", \"ip2\", ...],\n\
    \"order\": [\"ip1\", \"ip2\", ...],\n\
    \"user_port\": port_number,\n\
    \"product_port\": port_number,\n\
    \"order_port\": port_number\n\
}\n\
\n\
"
parser = argparse.ArgumentParser(description=DESCRIPTION, \
        formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("port", type=int, nargs="?", default=7999, \
        help="The port number for the ISCS module (default: 7999)")
parser.add_argument("-d", "--debug", action="store_true", help="Enable debug mode", default=False)

# Global variables

ORDER_PORT = 7998
ISCS_PORT = 7999
USER_PORT = 8000
PRODUCT_PORT = 9000

USER_IPS = ["localhost"]
PRODUCT_IPS = ["localhost"]
ORDER_IPS = ["localhost"]

# LEN OF USER_IPS AND PRODUCT_IPS
LEN_USER_IPS = 1
LEN_PRODUCT_IPS = 1
LEN_ORDER_IPS = 1

# Current index of the user and product services
USER_INDEX = 0
PRODUCT_INDEX = 0
ORDER_INDEX = 0

app = Flask(__name__)

# Redirect routes
@app.route('/<endpoint>', methods=['POST'])
def forward_request(endpoint):
    """Forward the request to the appropriate service.
    possible endpoints include:
    /user -> forwarded to user Services
    /product -> forwarded to product Services
    /order -> forwarded to order Services
    """
    # Get the next IP address for the given service
    next_ip = get_next_service_ip(endpoint)

    # Debugging info
    if app.debug:
        if next_ip:
            print(f"Forwarding request to {next_ip}/{endpoint}", file=sys.stderr)
        else:
            print(f"Invalid endpoint: {endpoint}", file=sys.stderr)

    if next_ip:
        # Forward the request to the next service
        return redirect(f"{next_ip}/{endpoint}", code=307)

    # Endpoint is invalid
    return jsonify({"error": f"Invalid endpoint /{endpoint}"}), 400


# GET routes
@app.route('/user/purchased/<_id>', methods=['GET'])
def forward_order_history_request(_id):
    """Forward the request to the Order service.
    the only possible endpoint is:
    /user/purchased/<id> -> forwarded to order Services
    """
    # Get the next IP address for the given service
    next_ip = get_next_service_ip("order")
    # Debugging info
    if app.debug:
        print(f"Forwarding request to {next_ip}/order/purchased/{_id}", file=sys.stderr)

    # Forward the request to the next service
    return redirect(f"{next_ip}/order/purchased/{_id}", code=307)

@app.route('/<endpoint>/<_id>', methods=['GET'])
def forward_request_with_id(endpoint, _id):
    """Forward the request to the appropriate service.
    possible endpoints include:
    /user/<id> -> forwarded to user Services
    /product/<id> -> forwarded to product Services
    """
    # Get the next IP address for the given service
    next_ip = get_next_service_ip(endpoint)

    # Debugging info
    if app.debug:
        if next_ip:
            print(f"Forwarding request to {next_ip}/{endpoint}/{_id}", file=sys.stderr)
        else:
            print(f"Invalid endpoint: {endpoint}", file=sys.stderr)

    if next_ip:
        # Forward the request to the next service
        return redirect(f"{next_ip}/{endpoint}/{_id}", code=307)
    # Invalid endpoint
    return jsonify({"error": "ISCS Invalid endpoint"}), 400

# Error handlers
# Error codes using the 4xx and 5xx ranges
error_codes = [400, 401, 403, 404, 405, 500, 501, 502, 503, 504]
for _code in error_codes:
    @app.errorhandler(_code)
    def error_handler(error, code=_code):
        """Error handler for the given error code."""
        return jsonify({"error": f"{error}"}), code

# Helper functions

def get_next_service_ip(endpoint_name):
    """Get the IP address with port of the service with the given endpoint name.
    """
    # Get the next IP address for the given service
    if endpoint_name == "user":
        global USER_INDEX
        ip = USER_IPS[USER_INDEX]
        USER_INDEX = (USER_INDEX + 1) % LEN_USER_IPS
        return f"http://{ip}:{USER_PORT}"
    if endpoint_name == "product":
        global PRODUCT_INDEX
        ip = PRODUCT_IPS[PRODUCT_INDEX]
        PRODUCT_INDEX = (PRODUCT_INDEX + 1) % LEN_PRODUCT_IPS
        return f"http://{ip}:{PRODUCT_PORT}"
    if endpoint_name == "order":
        global ORDER_INDEX
        ip = ORDER_IPS[ORDER_INDEX]
        ORDER_INDEX = (ORDER_INDEX + 1) % LEN_ORDER_IPS
        return f"http://{ip}:{ORDER_PORT}"
    return None

def read_ips():
    """Read in the IP addresses of the user and product services.
    These IP addresses are read in from a file called "ips.json" which is located in the same
    directory as this file. The file should be in the following format:
    {
        "user": ["ip1", "ip2", ...],
        "product": ["ip1", "ip2", ...]
        "order": ["ip1", "ip2", ...]
        "user_port": port_number,
        "product_port": port_number,
        "order_port": port_number
    }
    """
    global USER_IPS, PRODUCT_IPS, ORDER_IPS, USER_PORT, PRODUCT_PORT, ORDER_PORT
    global LEN_USER_IPS, LEN_PRODUCT_IPS, LEN_ORDER_IPS

    # change directory to the current file's directory (to read in the ips.json file)
    os.chdir(os.path.dirname(os.path.abspath(__file__)))

    with open("ips.json", "r", encoding="ASCII") as file:
        data = json.load(file)
        USER_IPS = data["user"]
        PRODUCT_IPS = data["product"]
        ORDER_IPS = data["order"]
        USER_PORT = data["user_port"]
        PRODUCT_PORT = data["product_port"]
        ORDER_PORT = data["order_port"]

    LEN_USER_IPS = len(USER_IPS)
    LEN_PRODUCT_IPS = len(PRODUCT_IPS)
    LEN_ORDER_IPS = len(ORDER_IPS)

def _debug_info():
    """Print out the configuration of the ISCS module.
    """
    print("ISCS Configuration:", file=sys.stderr)
    print(f"  ISCS Port: {ISCS_PORT}", file=sys.stderr)
    # Print out the user service IPs and port
    print(f"  User Services: (total: {LEN_USER_IPS})", file=sys.stderr)
    for ip in USER_IPS:
        print(f"    {ip}:{USER_PORT}", file=sys.stderr)

    # Print out the product service IPs and port
    print(f"  Product Services: (total: {LEN_PRODUCT_IPS})", file=sys.stderr)
    for ip in PRODUCT_IPS:
        print(f"    {ip}:{PRODUCT_PORT}", file=sys.stderr)

    # Print out the order service IPs and port
    print(f"  Order Services: (total: {LEN_PRODUCT_IPS})", file=sys.stderr)
    for ip in ORDER_IPS:
        print(f"    {ip}:{ORDER_PORT}", file=sys.stderr)

def main():
    """Main function for the ISCS module. This function reads in the command line arguments,
    prints out the configuration, and starts listening for requests.
    """
    # Read in the command line arguments
    args = parser.parse_args()
    global ISCS_PORT
    ISCS_PORT = args.port
    if args.debug:
        app.debug = args.debug

    # Read in the IP addresses of the user, product and order services
    read_ips()

    # Print out the configuration if the app is running with the debug flag
    if app.debug:
        _debug_info()

    # Disable all logging for performance
    if not app.debug:
        print("Running in non-debug mode", file=sys.stderr)
        print("Run with -h or --help for more information", file=sys.stderr)
        log = logging.getLogger('werkzeug')
        log.setLevel(logging.ERROR)

    # Start listening for requests with a multi-threaded server
    app.run(host="0.0.0.0", port=ISCS_PORT, threaded=True)

if __name__ == "__main__":
    main()
