import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * This is the microservice that handles HTTP requests related to orders and returns a response back to the client.
 * If required, it will send an HTTP request to the UserService or ProductService and relay its response to the client.
 */
public class OrderService
{
    static final OrderDatabase orderDB = new OrderDatabase();
    /**
     * The main method starts the server that is used to handle orders, and sets up the current working directory.
     *
     * @param args Command-line arguments passed to the program. The argument should be the current working directory.
     */
    public static void main(String[] args) throws IOException
    {
        String ip = "0.0.0.0";
        int port = -1;
        String ISCS_IP = "";

        // Get Port and ISCS_IP from the command line
        if (args.length > 1)
        {
            ISCS_IP = args[1];
            port = Integer.parseInt(args[0]);
        }
        else
        {
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);

        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed

        // Set up context for a POST request to the OrderService
        server.createContext("/order", new OrderHandler());
        server.createContext("/user", new UserHandler(ISCS_IP));
        server.createContext("/product", new ProductHandler(ISCS_IP));

        // Set up context for a Get request to the OrderService
        server.createContext("/user/purchased/", new PurchaseHandler());

        server.setExecutor(null); // creates a default executor

        server.start();

        //("Server started on port " + port);
    }

    /**
     * A custom HTTP handler for processing POST requests related to orders.
     * Implements the HttpHandler interface to handle HTTP exchanges.
     */
    static class OrderHandler implements HttpHandler
    {
        /**
         * Handles the incoming HTTP exchange, processing POST requests for order creation.
         * Implements the HttpHandler interface to handle HTTP exchanges related to order management.
         * This method specifically handles the creation of orders when receiving a POST request to "/create".
         *
         * @param exchange The HttpExchange object representing the HTTP request and response.
         * @throws IOException If an I/O error occurs while handling the request.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                // Handle POST request for /order
                if ("POST".equals(exchange.getRequestMethod()))
                {
                    //Initialize variables
                    String orderData = OrderService.getRequestBody(exchange);
                    JSONObject jsonObject = new JSONObject(orderData);
                    String status_message = "Invalid Request";

                    //Verify that all fields are present for creation
                    if (!(jsonObject.has("product_id")
                            && jsonObject.has("user_id")
                            && jsonObject.has("quantity")))
                    {
                        sendResponse(exchange, 400, "{\"status\": \"Invalid Request\"}");
                        exchange.close();
                        return;
                    }

                    int userID = jsonObject.getInt("user_id");
                    int prodID = jsonObject.getInt("product_id");
                    int quantity = jsonObject.getInt("quantity");

                    if (orderDB.getUser(userID).equals("") || orderDB.getProduct(prodID).equals("")) {
                        // Send a 405 Method Not Allowed response for non-POST requests
                        sendResponse(exchange, 400, "{\"status\": \"Invalid Request\"}");
                    }

                    JSONObject product = new JSONObject(orderDB.getProduct(prodID));
                    int newQuantity = product.getInt("quantity") - quantity;
                    if (newQuantity < 0) {
                        // Send a 405 Method Not Allowed response for non-POST requests
                        sendResponse(exchange, 400, "{\"status\": \"Exceeded quantity limit\"}");
                    	return;
		    }

		    int statusCode = orderDB.placeOrder(userID, prodID, quantity, newQuantity);

                    if (statusCode != 200) {
                        jsonObject.put("status", status_message);
                    } else {
                        jsonObject.put("status", "Success"); // TEST
                    }
                    String response = jsonObject.toString();
                    sendResponse(exchange, statusCode, response);
                }
            
            }
            catch (Exception e)
            {
                // If something weird happens, we send a 400 error code representing an invalid HTTP request
                sendResponse(exchange, 400, "{\"status\": \"Invalid Request\"}");
            }
            exchange.close();
        }
    }

    /**
     * A custom HTTP handler for processing requests related to user management.
     * Implements the HttpHandler interface to handle HTTP exchanges.
     * This class is responsible for handling various user-related commands, such as create, delete, update, and get.
     */
    static class UserHandler implements HttpHandler
    {

        String uri;

        public UserHandler(String s)
        {
            this.uri = s;
        }

        /**
         * Handles the incoming HTTP exchange, processing requests related to user management.
         *
         * @param exchange The HttpExchange object representing the HTTP request and response.
         * @throws IOException If an I/O error occurs while handling the request.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                // Initialize variables
                String url = this.uri;
                String endpoint = "";
                String data = "";

                // Create a parsable JSONObject from our response body's string
                // JSONObject jsonObject = new JSONObject(data);

                // Parse the command
                String command = "";
			

                switch (exchange.getRequestMethod())
                {
                    case "POST":
                        endpoint = "/user";
                        command = "post";
                        data = getRequestBody(exchange);
			// JSONObject jsonObject = new JSONObject(data);
                        break;
                    case "GET":
                        String path = exchange.getRequestURI().getPath();
                        String[] pathParts = path.split("/");
                        if (pathParts.length != 3)
                        {
                            // Bad request
			    //("Hello from orderservice case GET");
                            sendResponse(exchange, 400, new JSONObject().toString());
                            return;
                        }
			//("pathParts.length");
                        endpoint = "/user/"+pathParts[2];
                        command = "get";
                        break;
                    default:
                        sendResponse(exchange, 400, new JSONObject().toString());
                }

                // Send an HTTP Request to another server, collect the response
                ResponseTuple tuple = OrderService.sendHTTPRequest(url + endpoint, data, command);

                // Send the response back to the client
                sendResponse(exchange, tuple.getStatus_code(), tuple.getResponse());
            }
            catch (Exception e)
            {
                //If we get a weird error, it's a bad HTTP request
		//(e);
		//("Hello from orderservice, it errors: 214");
                sendResponse(exchange, 400, new JSONObject().toString());
            }
            exchange.close();
        }
    }

    /**
     * A custom HTTP handler for processing requests related to product management.
     * Implements the HttpHandler interface to handle HTTP exchanges.
     * This class is responsible for handling various product-related commands, such as create, update, delete, and info.
     */
    static class ProductHandler implements HttpHandler
    {
        String uri;

        public ProductHandler(String s)
        {
            this.uri = s;
        }

        /**
         * Handles the incoming HTTP exchange, processing requests related to product management.
         *
         * @param exchange The HttpExchange object representing the HTTP request and response.
         * @throws IOException If an I/O error occurs while handling the request.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                // Initialize variables
                String url = this.uri;
                String endpoint = "";
                String data = "";

                String command = "";	

                switch (exchange.getRequestMethod())
                {
                    case "POST":
                            endpoint = "/product";
                            command = "post";
                            data = getRequestBody(exchange);
                            break;
                    case "GET":
			    String path = exchange.getRequestURI().getPath();
                            String[] pathParts = path.split("/");
                            if (pathParts.length != 3)
                            {
                                // Bad request
                                sendResponse(exchange, 400, new JSONObject().toString());
                                return;
                            }

                            endpoint = "/product/"+pathParts[2];
                            command = "get";
                            
			    //(url + endpoint);
			    
			    break;
                    default:
                            sendResponse(exchange, 400, new JSONObject().toString());
                }
                // Send an HTTP Request to another server, collect the response
                ResponseTuple tuple = OrderService.sendHTTPRequest(url + endpoint, data, command);

                // Send the response back to the client
                sendResponse(exchange, tuple.getStatus_code(), tuple.getResponse());
            }
            catch (Exception e)
            {
                //If we get a weird error, it's a bad HTTP request
                sendResponse(exchange, 400, new JSONObject().toString());
            }
            exchange.close();
        }
    }

    static class PurchaseHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                // Handle GET request for /user/purchased
                if ("GET".equals(exchange.getRequestMethod()))
                {
		    //("In the GET if statement");
                    //Initialize variables
                    String URI = exchange.getRequestURI().toString();
                    int userID = Integer.parseInt(URI.substring(16));
		    
		    //("==== URI ====");
		    //(URI);
		    
		    //("==== userID ====");
		    //(userID);
                    
		    String response = orderDB.getPurchased(userID);
		    //("==== response ====");
                    //(response);

                    if (response.equals("{}")) {
                        sendResponse(exchange, 404, new JSONObject().toString());
                    }
                    else {

                        sendResponse(exchange, 200, response);
                    }

                }
                else {
                    //status code 405 for non Get Requests
                    sendResponse(exchange, 405, new JSONObject().toString());
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                //If any weird error occurs, then UserService has received a bad http request
                sendResponse(exchange, 400, new JSONObject().toString());
            }
            exchange.close();
        }
    }

    /**
     * Sends an HTTP request to the specified URL with the provided data and command.
     *
     * @param url The URL to which the HTTP request is sent.
     * @param data The data to be included in the request body.
     * @param command The command indicating the type of HTTP request (e.g., GET, POST).
     * @return A ResponseTuple containing the response content and HTTP status code.
     * @throws IOException If an I/O error occurs during the HTTP request.
     * @throws URISyntaxException If the URL syntax is incorrect.
     */
    public static ResponseTuple sendHTTPRequest(String url, String data, String command) throws IOException, URISyntaxException {
        // Create a URL object
        URI serverUrl = new URI(url);

        // Open a connection to the URL
        HttpURLConnection connection = (HttpURLConnection) serverUrl.toURL().openConnection();

        if (command.equals("get"))
            connection.setRequestMethod("GET");
        else
        {
            // Set the request method to POST otherwise
            connection.setRequestMethod("POST");

            // Enable input/output streams
            connection.setDoOutput(true);

            // Set the request body content type
            connection.setRequestProperty("Content-Type", "application/json");

            // Get the output stream of the connection
            try (OutputStream os = connection.getOutputStream())
            {
                // Write the data to the output stream
                os.write(data.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        return readResponse(connection);
    }

    /**
     * Reads the response from an HttpURLConnection and constructs a ResponseTuple.
     *
     * @param con The HttpURLConnection from which to read the response.
     * @return A ResponseTuple containing the response content and HTTP status code.
     * @throws IOException If an I/O error occurs while reading the response.
     */
    public static ResponseTuple readResponse(HttpURLConnection con) throws IOException {
        StringBuilder response = new StringBuilder();
        int status_code = con.getResponseCode();
        String output = "{}";

        if (status_code == 200)
        {
            // Read the response content
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            output = response.toString();
        }


        ResponseTuple tuple = new ResponseTuple(output, status_code);

        con.disconnect();
        return tuple;
    }

    /**
     * Sends an HTTP response with the specified response code and content.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param rCode The HTTP response code to be sent.
     * @param response The content of the HTTP response body.
     * @throws IOException If an I/O error occurs while sending the response.
     */
    public static void sendResponse(HttpExchange exchange, int rCode, String response) throws IOException
    {
        exchange.sendResponseHeaders(rCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    /**
     * Reads the request body from an HttpExchange and returns it as a string.
     *
     * @param exchange The HttpExchange object representing the HTTP request.
     * @return The request body as a string.
     * @throws IOException If an I/O error occurs while reading the request body.
     */
    public static String getRequestBody(HttpExchange exchange) throws IOException
    {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)))
        {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
            {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }

    /**
     * A simple class representing a tuple containing an HTTP response and its status code.
     */
    static class ResponseTuple {
        String response;
        int status_code;

        /**
         * Constructs a ResponseTuple with the given response content and HTTP status code.
         *
         * @param response The content of the HTTP response body.
         * @param status_code The HTTP status code.
         */
        public ResponseTuple(String response, int status_code)
        {
            this.response = response;
            this.status_code = status_code;
        }

        /**
         * Gets the response content.
         *
         * @return The content of the HTTP response body.
         */
        public String getResponse() {return this.response;}

        /**
         * Gets the HTTP status code.
         *
         * @return The HTTP status code.
         */
        public int getStatus_code() {return this.status_code;}
    }
}
