'''
stress_test.py

This script is used to test the performance of the whole system.
Using 8 processes to send as many requests as possible to the server.

It will print out the calculated requests per second on 5 seconds interval.
'''

import argparse
import time
import random
from multiprocessing import Process, Array
import requests

parser = argparse.ArgumentParser(description='Stress test for the server.')
parser.add_argument('URL', type=str, help='entrypoint to the system.')
parser.add_argument('N', type=int, help='Number of users & products already in the database.')
parser.add_argument('T', type=int, help='Number of seconds to run the test.')

URL = ''
N = 0

TEST_TIME_IN_SECONDS = 10

COUNTERS = Array('i', 8)

HEADERS = {"Content-Type": "application/json"}

def send_requests(process_id, counters):
    """
    Send random valid requests to the server.

    NOTE: This test should be used AFTER N many users, products have been added to the database.

    This function will send:
    - 1/6 of the requests will be to get a random product
    - 1/6 of the requests will be to get a random user
    - 1/6 of the requests will be to get a random users order history
    - 1/2 of the requests will be to place a random order
    """
    # send requests to the server
    start = time.perf_counter()
    counter = 0
    while True:
        # send a random request
        try:
            match random.randint(1, 6):
                case 1:
                    get_product()
                case 2:
                    get_user()
                case 3:
                    get_order_history()
                case _:
                    place_order()
        # catch http request exceptions
        except requests.exceptions.RequestException as e:
            print(f'Process {process_id} encountered an error: {e}')
            break
        # update counter
        counter += 1
        # print out calculated requests per second every 5 seconds
        if time.perf_counter() - start >= 5:
            end = time.perf_counter()
            print(f'Process {process_id} has sent {counters[process_id] + counter} requests.')
            print(f'Process {process_id} has sent {counter / (end - start)} requests per second.')
            counters[process_id] += counter
            counter = 0
            start = time.perf_counter()

def get_product():
    """
    Send a request to the server to get a random product.
    """
    requests.get(f'{URL}/product/{random.randint(1, N)}', timeout=5)

def get_user():
    """
    Send a request to the server to get a random user.
    """
    requests.get(f'{URL}/user/{random.randint(1, N)}', timeout=5)

def get_order_history():
    """
    Send a request to the server to get a random users' order history.
    """
    requests.get(f'{URL}/user/purchased/{random.randint(1, N)}', timeout=5)
        
def place_order():
    """
    Send a request to the server to place a random order.
    """
    _json = {
        'command': 'place_order',
        'user_id': random.randint(1, N),
        'product_id': random.randint(1, N),
        'quantity': 0
    }
    requests.post(f'{URL}/order', json=_json, headers=HEADERS, timeout=5)
def main():
    """
    Main function to start the stress test.
    """
    # Parse commandline arguments
    global URL, N, TEST_TIME_IN_SECONDS
    args = parser.parse_args()
    URL = args.URL
    N = args.N
    TEST_TIME_IN_SECONDS = args.T
    # print out note:
    print("NOTE: This test should be used AFTER N many users, products have been added to the database.")
    # Print out the configuration
    print(f'Starting stress test with the following configuration:')
    print(f'URL: {URL}')
    print(f'N: {N}')
    print(f'TEST_TIME_IN_SECONDS: {TEST_TIME_IN_SECONDS}')

    # start 8 processes to send requests to the server
    processes = []
    for i in range(8):
        p = Process(target=send_requests, args=(i, COUNTERS))
        p.start()
        processes.append(p)
    # kill all processes after TEST_TIME_IN_SECONDS seconds
    time.sleep(TEST_TIME_IN_SECONDS)
    for p in processes:
        p.terminate()
    # print out the final result
    print('Stress test finished.')
    print('Final results:')
    for i in range(8):
        print(f'Process {i} has sent {COUNTERS[i]} requests.')
    # calculate the average requests per second
    total = sum(COUNTERS[:])
    print(f'Total requests sent: {total}')
    print(f'Average requests per second: {total / TEST_TIME_IN_SECONDS}')
    print(f'Average requests per second per process: {total / TEST_TIME_IN_SECONDS / 8}')


if __name__ == '__main__':
    main()
