'''
iscs.py

ISCS: Inter-System Communication System

This module works as a load balancer and a request forwarder for the
User, Product and Order systems.

Usage:
./iscs.py <order_service_port> <base_product_service_port>
          <num_product_services> <base_user_service_port> <num_user_services>
'''

import sys
from flask import Flask, redirect, jsonify

# Global variables
ISCS_PORT = 7999
ORDER_PORT = 7998
USER_PORT = 8000
PRODUCT_PORT = 9000
NUM_USER_SERVICES = 1
NUM_PRODUCT_SERVICES = 1
CURRENT_USER_SERVICE = 0
CURRENT_PRODUCT_SERVICE = 0

app = Flask(__name__)

# Redirect routes
@app.route('/<endpoint>', methods=['POST'])
def forward_request(endpoint):
    """Forward the request to the appropriate service.
    possible endpoints include:
    /user
    /product
    """
    # Get the port of the next available service
    port = get_port(endpoint)
    if port:
        # Forward the request
        return redirect(f"http://localhost:{port}/{endpoint}")
    # Invalid endpoint
    return jsonify({"error": "Invalid endpoint"}), 400

@app.route('/<endpoint>/<_id>', methods=['GET'])
def forward_request_with_id(endpoint, _id):
    """Forward the request to the appropriate service.
    possible endpoints include:
    /user/<id>
    /product/<id>
    """
    # Get the port of the next available service
    port = get_port(endpoint)
    if port:
        # Forward the request
        return redirect(f"http://localhost:{port}/{endpoint}/{_id}")
    # Invalid endpoint
    return jsonify({"error": "Invalid endpoint"}), 400

def get_port(endpoint):
    """Return the port of the next available service.
    """
    if "user" in endpoint:
        return next_user_service()
    if "product" in endpoint:
        return next_product_service()
    return None

def next_user_service():
    """Return the port of the next available user service.
    """
    global CURRENT_USER_SERVICE
    port = USER_PORT + (CURRENT_USER_SERVICE % NUM_USER_SERVICES)
    CURRENT_USER_SERVICE += 1
    return port

def next_product_service():
    """Return the port of the next available product service.
    """
    global CURRENT_PRODUCT_SERVICE
    port = PRODUCT_PORT + (CURRENT_PRODUCT_SERVICE % NUM_PRODUCT_SERVICES)
    CURRENT_PRODUCT_SERVICE += 1
    return port

def main():
    """Main function for the ISCS module. This function reads in the command line arguments,
    prints out the configuration, and starts listening for requests.
    """
    # Read in the command line arguments
    match (len(sys.argv)):
        case 7:
            # Use the command line arguments
            global ISCS_PORT, ORDER_PORT, PRODUCT_PORT
            global NUM_PRODUCT_SERVICES, USER_PORT, NUM_USER_SERVICES
            ISCS_PORT = int(sys.argv[1])
            ORDER_PORT = int(sys.argv[2])
            PRODUCT_PORT = int(sys.argv[3])
            NUM_PRODUCT_SERVICES = int(sys.argv[4])
            USER_PORT = int(sys.argv[5])
            NUM_USER_SERVICES = int(sys.argv[6])
        case 1:
            # Use defaults
            pass
        case _:
            # Invalid number of arguments
            print("Usage: ./iscs.py <port> <order_service_port> <base_product_service_port>",
                  "<num_product_services> <base_user_service_port> <num_user_services>")
            sys.exit(1)

    # Print out the configuration (print to stderr so it doesn't interfere with the output)
    print(f"ISCS_PORT: {ISCS_PORT}", file=sys.stderr)
    print(f"ORDER_PORT: {ORDER_PORT}", file=sys.stderr)
    print(f"PRODUCT_PORT: {PRODUCT_PORT}", file=sys.stderr)
    print(f"NUM_PRODUCT_SERVICES: {NUM_PRODUCT_SERVICES}", file=sys.stderr)
    print(f"USER_PORT: {USER_PORT}", file=sys.stderr)
    print(f"NUM_USER_SERVICES: {NUM_USER_SERVICES}", file=sys.stderr)

    # Start listening for requests with a multi-threaded server
    app.run(port=ISCS_PORT, threaded=True)

if __name__ == "__main__":
    main()
