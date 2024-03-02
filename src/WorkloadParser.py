import requests
import json
import sys
import os


def make_post_request(url, data):
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, data=data, headers=headers)
        if response.status_code == 200:
            print(f"POST request did work: {response.status_code}")  
            print("Response: ", response.text)

        else:
            print(f"POST request did not work: {response.status_code}")  
            print("Response: ", response.text)
    except Exception as e:
        print(e)

def make_get_request(url):
    try:
        headers = {'Accept': 'application/json', 'Authorization': 'Bearer your_token'}
        
        # Making a GET request
        response = requests.get(url, headers=headers)
        
        if response.status_code == 200:
            print(f"GET request succeeded: {response.status_code}")
            print("Response: ", response.text)
        else:
            print(f"GET request failed: {response.status_code}")
            print("Response: ", response.text)
    except Exception as e:
        print(e)

if __name__ == "__main__":

    if len(sys.argv) != 2:
        print("Usage: python WorkloadGenerator.py <file_path>")
        sys.exit(1)

    # Get the absolute path of this python script's directory
    script_dir = os.path.dirname(os.path.abspath(sys.argv[0]))

    # Initialize the file path for the workload file we are running
    file_path = os.path.abspath(sys.argv[1])

    # Change the working directory to the script's directory
    os.chdir(script_dir)
    
    # Read the content of config.json into a string
    with open('../config.json', 'r') as file:
        config_string = file.read()
    
    # Parse the JSON string into a dictionary
    config_data = json.loads(config_string)
    
    desired_key = "OrderService"
    if desired_key in config_data:
        url = "http://" + config_data["OrderService"]["ip"] + ":" + str(config_data["OrderService"]["port"])
    else:
        print(f"The key '{desired_key}' is not present in the configuration.")
        sys.exit(1)

    #Step 1: read in workload file
    # Open the file for reading

    try:
        with open(file_path, 'r') as file:
            # Read the entire content of the file
            for line in file:
                request_type = "POST"
                #Step 2: parse workload file
                tokens = line.split()
                data = {}
                
                try:
                    if tokens[0] == "USER":
                        endpoint = "/user"
                        data['command'] = tokens[1]                        
                        data['id'] = tokens[2]
                        
                        if data['command'] == "update":
                            # Only update the fields that are given
                            for remainingtoken in tokens[3:]:
                                key = remainingtoken.split(':')[0]
                                value = remainingtoken
                                data[key]=value
                        else:
                            # Otherwise all fields must be provided
                            data['username'] = tokens[3]
                            data['email'] = tokens[4]
                            data['password'] = tokens[5] 
                            
                    elif tokens[0] == "PRODUCT":
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
                            for remainingtoken in tokens[3:]:
                                key = remainingtoken.split(':')[0]
                                value = remainingtoken
                                data[key]=value
                        else:
                            # Otherwise all fields must be provided
                            data['name'] = tokens[3]
                            data['description'] = tokens[4]                        
                            data['price'] = tokens[5]
                            data['quantity'] = tokens[6]
                            
                    elif tokens[0] == "ORDER":
                        if tokens[1] == "get":
                            endpoint = "/user/purchased/" + tokens[2]
                            request_type = "GET"
                        else:
                            endpoint = "/order"
                            data['product_id'] = tokens[2]
                            data['user_id'] = tokens[3]
                            data['quantity'] = tokens[4]
                    
                    elif tokens[0] == 'restart':
                        endpoint = "/database"
                        data['command'] = "restart"
                    
                    elif tokens[0] == "shutdown":
                        endpoint = "/database"
                        data['command'] = "shutdown"
                    else:
                        endpoint = ""
                        print("error: unidentified token: " + tokens[0])
                
                except IndexError as e:
                    # continue when there are no more tokens left
                    pass
                
                
                #step 3: send http request to order_service 
                
                json_data = json.dumps(data)
                #print(json_data)
                #print(url + endpoint)
                if request_type == "POST":
                    make_post_request(url + endpoint, json_data)
                elif request_type == "GET":
                    make_get_request(url + endpoint)
                print("----------------------------------")
                
    except FileNotFoundError:
        print(f"File not found at path: {file_path}")
    
    
