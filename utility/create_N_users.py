'''
create_N_users.py
This script is used to create N users in the system.
'''

import sys
import time
import requests

# Defaults
# URL to create user
URL = "http://localhost:8069"
# Endpoint to create user
ENDPOINT = "/user"
# Number of users to create
N = 10
# Headers
HEADERS = {"Content-Type": "application/json"}

def create_n_users(url, n):
    """
    Create N users in the system. Print out the total time taken to create N users.
    :param url: URL to send POST request to create user.
    :param n: Number of users to create.
    """

    return_code = 0

    # Create N users
    start_time = time.time()
    for i in range(n):
        # User data
        user_data = {
            "command": "create",
            "id": str(i),
            "username": f"user_{i}",
            "email": f"user_{i}@mail.com",
            "password": f"password_{i}"
        }
        # Send POST request to create user
        # Stop the process if there is an error that prevents the rest of the 
        # users from being created
        try:
            response = requests.post(url, json=user_data, headers=HEADERS, timeout=5)
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
            print(f"Non 200 response creating user {i}: {response.status_code} {response.reason}")


    end_time = time.time()
    if return_code == 0:
        print(f"{n} users created in {end_time - start_time:.2f} seconds")
    else:
        print(f"Failed to create {n} users")
    return return_code
def _usage():
    """
    Print usage information.
    """
    print("Usage: python3 create_N_users.py [URL] [N]", file=sys.stderr)
    sys.exit(1)

def main():
    """
    Main function to create N users in the system.
    """
    # Read in command line arguments
    global URL, N
    match len(sys.argv):
        case 1:
            # Use default values
            pass
        case 2:
            # Use custom number of users or custom URL
            try:
                N = int(sys.argv[1])
            except ValueError:
                URL = sys.argv[1]
        case 3:
            # Use custom URL and number of users
            try:
                N = int(sys.argv[2])
            except ValueError:
                print("Invalid number of users", file=sys.stderr)
                _usage()
                sys.exit(1)
            URL = sys.argv[1]
        case _:
            print("Invalid number of arguments", file=sys.stderr)
            _usage()
            sys.exit(1)

    # Create N users
    sys.exit(create_n_users(URL+ENDPOINT, N))

if __name__ == "__main__":
    main()
