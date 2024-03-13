#!/bin/python3
'''
create_N_products.py
This script is used to create N products in the system.
'''

import sys
import time
from multiprocessing import Process
import argparse
import requests

parser = argparse.ArgumentParser(description="Send N create commands to URL/product at")
parser.add_argument("URL", help="URL to create product")
parser.add_argument("N", help="Number of products to create")
# Defaults
# URL to create product
URL = "http://localhost:8069"
# Endpoint to create product
ENDPOINT = "/product"
# Number of products to create
N = 10
# Number of processes
NUM_THREADS = 8

# Process return codes
RETURN_CODES = [0 for i in range(NUM_THREADS)]

# Headers
HEADERS = {"Content-Type": "application/json"}

def create_n_products(process_id, url):
    """
    create n products in the system in a strided manner
    :param url: URL to create product
    :param start_id: start id of product
    :param n: number of products to create after start_id

    This function is to be worked on by a process to attempt to create all N
    products, at a rate of REQUESTS_PER_SECOND.
    """
    return_code = 0

    # Get products start_id to n
    start_id = process_id * (N // NUM_THREADS)
    n = (process_id + 1) * (N // NUM_THREADS)

    start_time = time.perf_counter()
    for _i in range(start_id, n):
        # Product data
        data = {
            "command": "create",
            "id": _i,
            "name": "product_" + str(_i),
            "description": "description_" + str(_i),
            "price": _i * 10.0,
            "quantity": 100 * _i

        }

        # Send POST request to create product
        # Stop the process if there is an error that prevents the rest of the
        # products from being created
        try:
            response = requests.post(url, json=data, headers=HEADERS, timeout=5)
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
            print("Non 200 response creating product:", _i, \
                    response.status_code, response.text, file=sys.stderr)

    end_time = time.perf_counter()

    if return_code == 0:
        print("Process", process_id, "took", end_time - start_time, "seconds", file=sys.stderr)
    else:
        print("Process", process_id, "failed", file=sys.stderr)

    RETURN_CODES[process_id] = return_code

def main():
    """
    Main function to create N products in the system.
    """

    # Read in command line arguments
    # URL, N are all optional
    global URL, N
    args = parser.parse_args()
    if args.URL:
        URL = args.URL
    if args.N:
        try:
            N = int(args.N)
        except ValueError:
            print("N must be an integer", file=sys.stderr)
            parser.print_usage()
            sys.exit(1)

    # Get N products at a rate of REQUESTS_PER_SECOND per process
    print("Creating", N, "products with 8 processes", file=sys.stderr)
    # Print out statistics
    print("Each process will create ", N // NUM_THREADS, "products", file=sys.stderr)
    for i in range(NUM_THREADS):
        print("Process", i, "will create products",
              i * (N // NUM_THREADS), "to", (i + 1) * (N // NUM_THREADS), file=sys.stderr)

    # Create processes (one for each core on the machine)
    processes = []
    start_time = time.perf_counter()
    for i in range(NUM_THREADS):
        process = Process(target=create_n_products, args=(i, URL+ENDPOINT))
        process.start()
        processes.append(process)

    # Wait for all processes to finish
    for process in processes:
        process.join()
    end_time = time.perf_counter()

    print("All processes finished (took", end_time - start_time, "seconds).", file=sys.stderr)
    # Print out statistics

    if 1 in RETURN_CODES:
        print("One or more processes failed.", file=sys.stderr)
        sys.exit(1)

    print(f"Created {N} products ({N} requests total)", file=sys.stderr)
    print("On", NUM_THREADS, "processes.", file=sys.stderr)
    print("Total time:", end_time - start_time, "seconds.", file=sys.stderr)
    print("Average time per request:", (end_time - start_time) / N, "seconds.", file=sys.stderr)
    print("Average requests per second:", N / (end_time - start_time), file=sys.stderr)

if __name__ == "__main__":
    main()
