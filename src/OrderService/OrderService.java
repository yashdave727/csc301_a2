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
	String dockerIp, dbPort, redisPort;
        int port;

        // Get port to listen on
	// Get docker ip
	// Get db port
	// Get redis port
	if (args.length != 4)
        {
            System.out.println("Missing arguments <port> <dockerIp> <dbPort> <redisPort>");
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("Shutting down User Database connection pool...");
        orderDB.shutdownPool();
        }));

        port = Integer.parseInt(args[0]);
	dockerIp = args[1];
	dbPort = args[2];
	redisPort = args[3];
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);

        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed

        // Set up context for a POST request to the OrderService
        server.createContext("/order", new OrderHandler());

        // Set up context for a Get request to the OrderService
        server.createContext("/user/purchased/", new PurchaseHandler());

        server.setExecutor(null); // creates a default executor

	// Initialize the database with docker IP and ports
	orderDB.initialize(dockerIp, dbPort, redisPort);

        server.start();

	System.out.println("Order Service is running on port " + port);
	System.out.println("Docker IP: " + dockerIp);
	System.out.println("DB Port: " + dbPort);
	System.out.println("Redis Port: " + redisPort);
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
            //Initialize variables
            String orderData = OrderService.getRequestBody(exchange);
            JSONObject jsonObject = new JSONObject(orderData);
            try
            {

                // Handle POST request for /order
                if (!"POST".equals(exchange.getRequestMethod()))
		{
		    // Send a 405 Method Not Allowed response for non-POST requests
		    jsonObject.put("status", "Invalid Request");
		    sendResponse(exchange, 405, jsonObject.toString());
		    return;
		}
		// Check that the command is "place order"
		if (!jsonObject.has("command") || !jsonObject.getString("command").equals("place order")) {
			jsonObject.put("status", "Invalid Request");
			// Remove the command from the JSON object
			jsonObject.remove("command");
			sendResponse(exchange, 400, jsonObject.toString());
			return;
		}
		// Remove the command from the JSON object
		jsonObject.remove("command");


                //Verify that all fields are present for creation
                if (!(jsonObject.has("product_id")
			&& jsonObject.has("user_id")
                        && jsonObject.has("quantity")))
                {
		jsonObject.put("status", "Invalid Request");
                sendResponse(exchange, 400, jsonObject.toString());
                exchange.close();
                return;
                }

                int userID = jsonObject.getInt("user_id");
                int prodID = jsonObject.getInt("product_id");
                int quantity = jsonObject.getInt("quantity");

                if (orderDB.getUser(userID).equals("") || orderDB.getProduct(prodID).equals("")) {
			// Send a 405 Method Not Allowed response for non-POST requests
			jsonObject.put("status", "Invalid Request");
                        sendResponse(exchange, 400, jsonObject.toString());
			return;
                }

                JSONObject product = new JSONObject(orderDB.getProduct(prodID));
                int newQuantity = product.getInt("quantity") - quantity;
                if (newQuantity < 0) {
                        // Send a 405 Method Not Allowed response for non-POST requests
			jsonObject.put("status", "Invalid Request");
			sendResponse(exchange, 400, jsonObject.toString());
                    	return;
		}

		int statusCode = orderDB.placeOrder(userID, prodID, quantity, newQuantity);

                if (statusCode != 200) {
                        jsonObject.put("status", "Invalid Request");
                } else {
                        jsonObject.put("status", "Success"); // TEST
                }
                String response = jsonObject.toString();
                sendResponse(exchange, statusCode, response);
		return;
            }
            catch (Exception e)
            {
                // If something weird happens, we send a 400 error code representing an invalid HTTP request
		jsonObject.put("status", "Invalid Request");
                sendResponse(exchange, 400, jsonObject.toString());
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

		    String response = orderDB.getPurchased(userID);

                    if (response.equals("{}")) {
                        sendResponse(exchange, 404, new JSONObject().toString());
			return;
                    }
                    else {

                        sendResponse(exchange, 200, response);
			return;
                    }

                }
                else {
                    //status code 405 for non Get Requests
                    sendResponse(exchange, 405, new JSONObject().toString());
		    return;
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
     * Sends an HTTP response with the specified response code and content.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param rCode The HTTP response code to be sent.
     * @param response The content of the HTTP response body.
     * @throws IOException If an I/O error occurs while sending the response.
     */
    public static void sendResponse(HttpExchange exchange, int rCode, String response) throws IOException
    {
        // Convert the response String to bytes to correctly measure its length in bytes
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        // Set the necessary response headers before sending the response body
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

        // Correctly set the content length using the byte length of the response
        exchange.sendResponseHeaders(rCode, responseBytes.length);

        // Write the response bytes and close the OutputStream
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
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
}
