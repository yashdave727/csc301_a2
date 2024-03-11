'''
create_N_orders.py
This script is used to create N orders in the system.
'''

import sys
import time
import requests

# Defaults
# URL to create order
URL = "http://localhost:8068"
# Endpoint to create order
ENDPOINT = "/order"
# Number of orders to create
N = 10
# Headers
HEADERS = {"Content-Type": "application/json"}

def create_n_orders(url, n):
    """
    Create N orders in the system. Print out the total time taken to create N orders.
    :param url: URL to send POST request to create order.
    :param n: Number of orders to create.
    """

    return_code = 0

    # Create N orders
    start_time = time.time()
    for i in range(n):
        # Order data
        order_data = {
            "command": "place_order",
            "product_id": i, 
            "user_id": i,
            "quantity": i
        }
        # Send POST request to create order
        # Stop the process if there is an error that prevents the rest of the
        # orders from being created
        try:
            response = requests.post(url, json=order_data, headers=HEADERS, timeout=5)
            response.raise_for_status()
        except requests.exceptions.ConnectionError as e:
            print("Connection error:", e, file=sys.stderr)
            return_code = 1
            break
        except requests.exceptions.Timeout as e:
            print("Request timed out:", e, file=sys.stderr)
            return_code = 1
            break
        except requests.exceptions.HTTPError as e:
            print("HTTP error:", e, file=sys.stderr)
            return_code = 1
            break
        except requests.exceptions.TooManyRedirects as e:
            print("Too many redirects:", e, file=sys.stderr)
            return_code = 1
            break
        except requests.exceptions.RequestException as e:
            print("Request exception:", e, file=sys.stderr)
            return_code = 1
            break

        # Print response if unsuccessful
        if response.status_code != 200:
            print("Non 200 response creating order:", i, \
                    response.status_code, response.text, file=sys.stderr)


    end_time = time.time()
    if return_code == 0:
        print(f"{n} orders created in {end_time - start_time:.2f} seconds")
    else:
        print(f"Failed to create {n} orders")
    return return_code
def _usage():
    """
    Print usage information.
    """
    print("Usage: python3 create_N_orders.py [URL] [N]", file=sys.stderr)
    sys.exit(1)

def main():
    """
    Main function to create N orders in the system.
    """
    # Read in command line arguments
    global URL, N
    match len(sys.argv):
        case 1:
            # Use default values
            pass
        case 2:
            # Use custom number of orders or custom URL
            try:
                N = int(sys.argv[1])
            except ValueError:
                URL = sys.argv[1]
        case 3:
            # Use custom URL and number of orders
            try:
                N = int(sys.argv[2])
            except ValueError:
                print("Invalid number of orders", file=sys.stderr)
                _usage()
                sys.exit(1)
            URL = sys.argv[1]
        case _:
            print("Invalid number of arguments", file=sys.stderr)
            _usage()
            sys.exit(1)

    # Print out informative statement
    print(f"NOTE: {sys.argv[0]} should only be called AFTER N users, products have been created.", \
            file=sys.stderr)
    # Create N orders
    sys.exit(create_n_orders(URL+ENDPOINT, N))

if __name__ == "__main__":
    main()
