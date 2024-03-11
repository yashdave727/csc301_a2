'''
create_N_products.py
This script is used to create N products in the system.
'''

import sys
import time
import requests

# Defaults
# URL to create product
URL = "http://localhost:8065"
# Endpoint to create product
ENDPOINT = "/product"
# Number of products to create
N = 10
# Headers
HEADERS = {"Content-Type": "application/json"}

def create_n_products(url, n):
    """
    Create N products in the system. Print out the total time taken to create N users.
    :param url: URL to send POST request to create product.
    :param n: Number of products to create.
    """

    return_code = 0

    # Create N products
    start_time = time.time()
    for i in range(n):
        # Product data
        product_data = {
            "command": "create",
            "id": i,
            "name": f"product_{i}",
            "description": f"description_{i}",
            "price": i*10.0, # Must be a float
            "quantity": i*10 # Must be an integer
        }
        # Send POST request to create product
        # Stop the process if there is an error that prevents the rest of the
        # products from being created
        try:
            response = requests.post(url, json=product_data, headers=HEADERS, timeout=5)
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
            print("Non 200 response creating product:", i, \
                    response.status_code, response.text, file=sys.stderr)


    end_time = time.time()
    if return_code == 0:
        print(f"{n} products created in {end_time - start_time:.2f} seconds")
    else:
        print(f"Failed to create {n} products")
    return return_code
def _usage():
    """
    Print usage information.
    """
    print("Usage: python3 create_N_products.py [URL] [N]", file=sys.stderr)
    sys.exit(1)

def main():
    """
    Main function to create N products in the system.
    """
    # Read in command line arguments
    global URL, N
    match len(sys.argv):
        case 1:
            # Use default values
            pass
        case 2:
            # Use custom number of products or custom URL
            try:
                N = int(sys.argv[1])
            except ValueError:
                URL = sys.argv[1]
        case 3:
            # Use custom URL and number of products
            try:
                N = int(sys.argv[2])
            except ValueError:
                print("Invalid number of products", file=sys.stderr)
                _usage()
                sys.exit(1)
            URL = sys.argv[1]
        case _:
            print("Invalid number of arguments", file=sys.stderr)
            _usage()
            sys.exit(1)

    # Create N products
    sys.exit(create_n_products(URL+ENDPOINT, N))

if __name__ == "__main__":
    main()
