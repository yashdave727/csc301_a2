#!/bin/python3
'''
This script reads in a workload file and sends HTTP requests to the OrderService
'''
import json
import sys
import os
import requests

HEADERS = {'Content-Type': 'application/json'}

def make_post_request(url, data):
    '''Makes a POST request to the given URL with the given JSON data'''
    try:
        response = requests.post(url, json=data, headers=HEADERS, timeout=5)
        if response.status_code == 200:
            print(f"POST request did work: {response.status_code}")
            print("Response: ", response.text)

        else:
            print(f"POST request did not work: {response.status_code}")
            print("Response: ", response.text)
    except Exception as e:
        print(e)

def make_get_request(url):
    '''Makes a GET request to the given URL'''
    try:
        # Making a GET request
        response = requests.get(url, headers=HEADERS, timeout=5)

        if response.status_code == 200:
            print(f"GET request succeeded: {response.status_code}")
            print("Response: ", response.text)
        else:
            print(f"GET request failed: {response.status_code}")
            print("Response: ", response.text)
    except Exception as e:
        print(e)

def parse_and_send_request(url, line):
    '''Parses the given line and sends the appropriate request to the given URL'''
    request_type = "POST"
    #Step 2: parse workload file
    tokens = line.split()
    data = {}

    endpoint = ""

    try:
        match tokens[0]:
            case "USER":
                endpoint = "/user"
                data['command'] = tokens[1]
                data['id'] = tokens[2]
                if data['command'] == "update":
                    # Only update the fields that are given
                    data = _update_fields_that_are_given(data, tokens)
                else:
                    # Otherwise all fields must be provided
                    data['username'] = tokens[3]
                    data['email'] = tokens[4]
                    data['password'] = tokens[5]

            case "PRODUCT":
                endpoint = "/product"
                data['command'] = tokens[1]
                data['id'] = tokens[2]
                if data['command'] == "delete":
                    # API format for delete doesn't take description
                    data['name'] = tokens[3]
                    data['price'] = tokens[4]
                    data['quantity'] = tokens[5]
                elif data['command'] == "update":
                    # Only update the fields that are given
                    data = _update_fields_that_are_given(data, tokens)
                else:
                    # Otherwise all fields must be provided
                    data['name'] = tokens[3]
                    data['description'] = tokens[4]
                    data['price'] = tokens[5]
                    data['quantity'] = tokens[6]

            case "ORDER":
                if tokens[1] == "get":
                    endpoint = "/user/purchased/" + tokens[2]
                    request_type = "GET"
                else:
                    endpoint = "/order"
                    data['product_id'] = tokens[2]
                    data['user_id'] = tokens[3]
                    data['quantity'] = tokens[4]

            case "restart":
                endpoint = "/database"
                data['command'] = "restart"

            case "shutdown":
                endpoint = "/database"
                data['command'] = "shutdown"

            case _:
                print("error: unidentified token: " + tokens[0])

    except IndexError:
        # continue when there are no more tokens left
        pass

    #step 2.5: Convert id, user_id, product_id, quantity to int, convert price to float
    data = _convert_to_int_or_float(data)

    #step 3: send http request to order_service
    send_http_request(url, endpoint, data, request_type)
    print("----------------------------------")

def _update_fields_that_are_given(data, tokens):
    '''Updates the fields in the given data dictionary that are given in the given tokens list'''
    for remainingtoken in tokens[3:]:
        key = remainingtoken.split(':')[0]
        value = remainingtoken.split(':')[1]
        data[key]=value
    return data

def _convert_to_int_or_float(data):
    '''Converts the values of the given dictionary to int or float'''
    #step 2.5: Convert id, user_id, product_id, quantity to int, convert price to float
    for key in data:
        if key in ["id", "user_id", "product_id", "quantity"]:
            data[key] = int(data[key])
        elif key == "price":
            data[key] = float(data[key])

    return data

def send_http_request(url, endpoint, data, request_type):
    '''Sends an HTTP request to the given URL and endpoint with the given JSON and request type'''
    if request_type == "POST":
        make_post_request(url + endpoint, data)
    elif request_type == "GET":
        make_get_request(url + endpoint)

def main():
    '''Main function'''
    if len(sys.argv) != 2:
        print("Usage: python WorkloadGenerator.py <path_to_workload_file>")
        sys.exit(1)

    # Get the absolute path of this python script's directory
    script_dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    # Initialize the file path for the workload file we are running
    file_path = os.path.abspath(sys.argv[1])
    # Change the working directory to the script's directory
    os.chdir(script_dir)

    # Read the content of config.json into a string
    with open('../config.json', 'r', encoding="ASCII") as file:
        config_string = file.read()
    # Parse the JSON string into a dictionary
    config_data = json.loads(config_string)

    desired_key = "OrderService"
    if desired_key in config_data:
        os_url = "http://" + config_data["OrderService"]["ip"] + ":" + \
                str(config_data["OrderService"]["port"])
    else:
        print(f"The key '{desired_key}' is not present in the configuration.")
        sys.exit(1)

    #Step 1: read in workload file
    # Open the file for reading
    try:
        with open(file_path, 'r', encoding="ASCII") as file:
            for line in file:
                # Step 2 & 3: parse workload file and send request
                parse_and_send_request(os_url, line)

    except FileNotFoundError:
        print(f"File not found at path: {file_path}")

if __name__ == "__main__":
    main()
