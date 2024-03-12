'''
get_N_users.py
This script is used to get N users in the system.
'''

import sys
import time
from multiprocessing import Process
import argparse
import requests

parser = argparse.ArgumentParser(description="Send N GET requests to URL/user/<id> at " +
                                 "a rate of REQUESTS_PER_SECOND per process.")
parser.add_argument("URL", help="URL to get user")
parser.add_argument("N", help="Number of users to get")
# Defaults
# URL to get user
URL = "http://localhost:8069"
# Endpoint to get user
ENDPOINT = "/user/"
# Number of users to get
N = 10
# Number of processes
NUM_THREADS = 8

# Process return codes
RETURN_CODES = [0 for i in range(NUM_THREADS)]

# Headers
HEADERS = {"Content-Type": "application/json"}

def get_n_users(process_id, url):
    """
    get n users in the system in a strided manner
    :param url: URL to get user
    :param start_id: start id of user
    :param n: number of users to get after start_id

    This function is to be worked on by a process to attempt to get all N
    users, at a rate of REQUESTS_PER_SECOND.
    """
    return_code = 0

    # Get users start_id to n
    start_id = process_id * (N // NUM_THREADS)
    n = (process_id + 1) * (N // NUM_THREADS)

    start_time = time.perf_counter()
    for _i in range(start_id, n):
        # User data
        i = str(_i)

        # Send GET request to get user
        # Stop the process if there is an error that prevents the rest of the
        # users from being getd
        try:
            response = requests.get(url+i, headers=HEADERS, timeout=5)
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
            print("Non 200 response creating user:", i, \
                    response.status_code, response.text, file=sys.stderr)

    end_time = time.perf_counter()

    if return_code == 0:
        print("Process", process_id, "took", end_time - start_time, "seconds", file=sys.stderr)
    else:
        print("Process", process_id, "failed", file=sys.stderr)

    RETURN_CODES[process_id] = return_code

def main():
    """
    Main function to get N users in the system.
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

    # Get N users at a rate of REQUESTS_PER_SECOND per process
    # Create processes (one for each core on the machine)
    print("Getting", N, "users with 8 processes", file=sys.stderr)
    processes = []
    start_time = time.perf_counter()
    for i in range(NUM_THREADS):
        process = Process(target=get_n_users, args=(i, URL+ENDPOINT))
        process.start()
        processes.append(process)
    # Print out statistics
    print("Each process:")
    print("will get ", N // NUM_THREADS, "users", file=sys.stderr)
    for i in range(NUM_THREADS):
        print("Process", i, "will get users",
              i * (N // NUM_THREADS), "to", (i + 1) * (N // NUM_THREADS), file=sys.stderr)

    # Wait for all processes to finish
    for process in processes:
        process.join()
    end_time = time.perf_counter()

    print("All processes finished (took", end_time - start_time, "seconds).", file=sys.stderr)
    # Print out statistics

    if 1 in RETURN_CODES:
        print("One or more processes failed.", file=sys.stderr)
        sys.exit(1)

    print(f"Got {N} users ({N} requests total)", file=sys.stderr)
    print("On", NUM_THREADS, "processes.", file=sys.stderr)
    print("Total time:", end_time - start_time, "seconds.", file=sys.stderr)
    print("Average time per request:", (end_time - start_time) / N, "seconds.", file=sys.stderr)
    print("Average requests per second:", N / (end_time - start_time), file=sys.stderr)

if __name__ == "__main__":
    main()
