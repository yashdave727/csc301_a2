#!/bin/python3
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/', defaults={'path': ''})
@app.route('/<path:path>', methods=['GET', 'POST'])
def catch_all(path):
    # Get the type of request
    request_type = request.method

    # Get the endpoint
    endpoint = request.path

    # Print out the type of request and endpoint
    # print("Received {} request to endpoint: {}".format(request_type, endpoint))

    # Check if JSON data is sent with the request
    if request.is_json:
        json_data = request.json
        # Return the JSON data and the type of request and endpoint
        return jsonify({'data': json_data, 'request_type': request_type, 'endpoint': endpoint})

    return jsonify({'data': 'No JSON data sent', 'request_type': request_type, 'endpoint': endpoint})

if __name__ == '__main__':
    # Disable all logging except for what is printed in catch_all
    import logging
    log = logging.getLogger('werkzeug')
    log.disabled = True


    app.run(debug=True, threaded=True)
