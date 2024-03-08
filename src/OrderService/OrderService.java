import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import org.json.*;

/**
 * This is the microservice that handles HTTP requests related to orders and returns a response back to the client.
 * If required, it will send an HTTP request to the UserService or ProductService and relay its response to the client.
 */
public class OrderService
{
    /**
     * The main method starts the server that is used to handle orders, and sets up the current working directory.
     *
     * @param args Command-line arguments passed to the program. The argument should be the current working directory.
     */
    public static void main(String[] args) throws IOException
    {
        // Change the working directory to the a1 folder
        if (args.length > 0)
        {
            // Take the absolute working directory from the commmand line
            String directoryPath = args[0];
            File directory = new File(directoryPath);

            // Check if the directory exists before attempting to change to it
            if (directory.exists() && directory.isDirectory())
            {
                // Change the current working directory
                System.setProperty("user.dir", directory.getAbsolutePath());
            }
            else
            {
                System.out.println("The working directory does not exist.");
                System.exit(1);
            }
        }
        else
        {
            System.out.println("No command-line arguments provided.");
            System.exit(1);
        }

        int port = Integer.parseInt(OrderService.getIPorPORT("PORT", 'O'));
        String ip = OrderService.getIPorPORT("IP", 'O');
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);

        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed

        // Set up context for a POST request to the OrderService
        server.createContext("/order", new OrderHandler());
        server.createContext("/user", new UserHandler());
        server.createContext("/product", new ProductHandler());
        server.createContext("/database", new DatabaseHandler());

        // Set up context for a Get request to the OrderService
        server.createContext("/user/purchased/", new PurchaseHandler());

        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
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
                // Handle POST request for /create
                if ("POST".equals(exchange.getRequestMethod()))
                {
                    //Initialize variables
                    String orderData = OrderService.getRequestBody(exchange);
                    JSONObject jsonObject = new JSONObject(orderData);

                    String userPath = System.getProperty("user.dir") + "/compiled/UserService/user_database.json";
                    String productPath = System.getProperty("user.dir") + "/compiled/ProductService/product_database.json";

                    boolean validUser = false;
                    int rCode = 400;
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


                    //Read our user database, and fill it into a JSONArray
                    FileReader userFileReader = new FileReader(userPath);
                    JSONTokener userTokener = new JSONTokener(userFileReader);
                    JSONArray user_jsonArray = new JSONArray(userTokener);

                    //Verify that a valid user_id exists in the user database
                    for (int i = 0; i < user_jsonArray.length(); i++)
                    {
                        if (user_jsonArray.getJSONObject(i).getInt("id") == jsonObject.getInt("user_id"))
                        {
                            validUser = true;
                        }
                    }

                    //Read our product database, and fill it into a JSONArray
                    FileReader productFileReader = new FileReader(productPath);
                    JSONTokener productTokener = new JSONTokener(productFileReader);
                    JSONArray product_jsonArray = new JSONArray(productTokener);

                    if (validUser)
                    {
                        //Verify that a valid product_id exists in the product database
                        for (int i = 0; i < product_jsonArray.length(); i++)
                        {
                            if (product_jsonArray.getJSONObject(i).getInt("id") == (jsonObject.getInt("product_id")))
                            {
                                //make sure the updated quantity (post order) can't go below zero
                                int updated_quantity = product_jsonArray.getJSONObject(i).getInt("quantity") - jsonObject.getInt("quantity");
                                if (updated_quantity >= 0)
                                {
                                    //update quantity in product database
                                    product_jsonArray.getJSONObject(i).put("quantity", String.valueOf(updated_quantity));
                                    status_message = "Success";
                                    rCode = 200;

                                    // Write the entire JSONArray to the file
                                    try (FileWriter fileWriter = new FileWriter(productPath))
                                    {
                                        product_jsonArray.write(fileWriter);
                                    }
                                    catch (IOException e)
                                    {
                                        e.printStackTrace();
                                    }



                                    // Iterate to the user who ordered the product and update order info in User Database
                                    for (int j = 0; j < user_jsonArray.length(); j++)
                                    {
                                        if (user_jsonArray.getJSONObject(j).getInt("id") == (jsonObject.getInt("user_id")))
                                        {
                                            JSONObject ordersJSON = user_jsonArray.getJSONObject(j).getJSONObject("orders");

                                            // if user has already bought this product then accumulate its quantity with the existing quantity
                                            if (ordersJSON.has(String.valueOf(jsonObject.getInt("product_id"))))
                                            {
                                                int current_quantity = ordersJSON.getInt(String.valueOf(jsonObject.getInt("product_id")));
                                                int num_purchased = jsonObject.getInt("quantity");
                                                int accumulated_quantity = current_quantity + num_purchased;
                                                ordersJSON.put(String.valueOf(jsonObject.getInt("product_id")), accumulated_quantity);
                                            }
                                            // otherwise create a new product_id key and add it to the ordersJSON in format {product_id, quantity}
                                            else
                                            {
                                                ordersJSON.put(String.valueOf(jsonObject.getInt("product_id")), jsonObject.getInt("quantity"));
                                            }

                                            user_jsonArray.getJSONObject(j).put("orders", ordersJSON);


                                            // Write the entire User's JSONArray to the file
                                            try (FileWriter fileWriter = new FileWriter(userPath))
                                            {
                                                user_jsonArray.write(fileWriter);
                                            }
                                            catch (IOException e)
                                            {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                else
                                {
                                    status_message = "Exceeded quantity limit";
                                    rCode = 400;
                                }
                            }
                        }
                    }

                    //add status message and send response
                    jsonObject.put("status", status_message);
                    String response = jsonObject.toString();
                    sendResponse(exchange, rCode, response);

                    userFileReader.close();
                    userTokener.close();
                    productFileReader.close();
                    productTokener.close();
                }
                else
                {
                    // Send a 405 Method Not Allowed response for non-POST requests
                    sendResponse(exchange, 405, "{\"status\": \"Invalid Request\"}");
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
                String url = "http://" + getIPorPORT("IP", 'U') + ":" + getIPorPORT("PORT", 'U');
                String endpoint;
                String data = getRequestBody(exchange);

                // Create a parsable JSONObject from our response body's string
                JSONObject jsonObject = new JSONObject(data);

                // Parse the command
                String command = jsonObject.getString("command");

                switch (command)
                {
                    case "create": case "delete": case "update":
                        endpoint = "/user";
                        break;
                    case "get":
                        endpoint = "/user/"+jsonObject.getInt("id");
                        break;
                    default:
                    {
                        sendResponse(exchange, 400, new JSONObject().toString()); return;
                    }
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

    /**
     * A custom HTTP handler for processing requests related to product management.
     * Implements the HttpHandler interface to handle HTTP exchanges.
     * This class is responsible for handling various product-related commands, such as create, update, delete, and info.
     */
    static class ProductHandler implements HttpHandler
    {
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
                String url = "http://" + getIPorPORT("IP", 'P') + ":" + getIPorPORT("PORT", 'P');
                String endpoint = "";
                String data = getRequestBody(exchange);

                // Create a parsable JSONObject from our response body's string
                JSONObject jsonObject = new JSONObject(data);

                // Parse the command
                String command = jsonObject.getString("command");

                switch (command)
                {
                    case "create": case "update": case "delete":
                            endpoint = "/product";
                            break;
                    case "info":
                            endpoint = "/product/"+jsonObject.getInt("id");
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


    /**
     * A custom HTTP handler for processing database-related commands.
     * Implements the HttpHandler interface to handle HTTP exchanges.
     * This class is responsible for handling commands such as shutdown and restart, affecting both user and product services.
     */
    static class DatabaseHandler implements HttpHandler
    {
        /**
         * Handles the incoming HTTP exchange, processing database-related commands.
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
                String urlUser = "http://" + getIPorPORT("IP", 'U') + ":" + getIPorPORT("PORT", 'U') + "/user";
                String urlProduct = "http://" + getIPorPORT("IP", 'P') + ":" + getIPorPORT("PORT", 'P') + "/product";
                String data = getRequestBody(exchange);

                // Create a parsable JSONObject from our response body's string
                JSONObject jsonObject = new JSONObject(data);

                // Parse the command
                String command = jsonObject.getString("command");

                switch (command)
                {
                    case "shutdown":
                    case "restart":
                    {
                        ResponseTuple order = OrderService.sendHTTPRequest(urlUser, data, command);
                        ResponseTuple user = OrderService.sendHTTPRequest(urlProduct, data, command);

                        if (order.getStatus_code() == 200 && user.getStatus_code() == 200)
                            sendResponse(exchange, order.getStatus_code(), order.getResponse());
                        else
                            {sendResponse(exchange, 400, new JSONObject().toString()); return;}
                        break;
                    }
                    default: {sendResponse(exchange, 400, new JSONObject().toString()); return;}
                }
            }
            catch (Exception e)
            {
                //If we get a weird error, it's a bad HTTP
                e.printStackTrace();
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
                    //Initialize variables
                    String URI = exchange.getRequestURI().toString();
                    int userID = Integer.parseInt(URI.substring(16));
                    String response = "";
                    boolean validSearch = false;

                    // Specify the file path where you want to search the JSON data
                    String filePath = System.getProperty("user.dir") + "/compiled/UserService/user_database.json";

                    // Read our JSON file database, and fill it into a JSONArray
                    FileReader fileReader = new FileReader(filePath);
                    JSONTokener tokener = new JSONTokener(fileReader);
                    JSONArray jsonArray = new JSONArray(tokener);

                    //find the matching user id within our jsonArray
                    for (int i = 0; i < jsonArray.length(); i++)
                    {
                        if (jsonArray.getJSONObject(i).getInt("id") == (userID))
                        {
                            response = jsonArray.getJSONObject(i).get("orders").toString();
                            validSearch = true;
                        }
                    }

                    if (validSearch)
                    {
                        sendResponse(exchange, 200, response);
                    }
                    else
                    {
                        //status code 404 id does not exist
                        sendResponse(exchange, 404, new JSONObject().toString());
                    }
                    fileReader.close();
                    tokener.close();
                }
                else
                {
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

        if (command.equals("get") || command.equals("info"))
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

        // Read the response content
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream())))
        {
            String inputLine;
            while ((inputLine = in.readLine()) != null)
            {
                response.append(inputLine);
            }
        }

        String output =  response.toString();
        int status_code = con.getResponseCode();

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
     * Retrieves the IP address or port number from the config.json file based on the specified type (IPorPORT) and service (UPO).
     *
     * @param IPorPORT Either "IP" or "PORT" indicating whether to retrieve the IP address or port number.
     * @param UPO The service identifier ('U' for UserService, 'P' for ProductService, 'O' for OrderService).
     * @return The retrieved IP address or port number as a string.
     */
    public static String getIPorPORT(String IPorPORT, char UPO){

        try {

            // Get the current directory
            String currentDirectory = System.getProperty("user.dir");

            // Navigate to the parent directory twice
            Path parentDirectory = Paths.get(currentDirectory);

            // Specify the path to the config.json file
            Path configFilePath = parentDirectory.resolve("config.json");

            // Read the content of the config.json file into a string
            String content = new String(Files.readAllBytes(configFilePath));

            // Parse the string into a JSONObject
            JSONObject jsonObject = new JSONObject(content);

            if (UPO == 'U'){
                JSONObject US = jsonObject.getJSONObject("UserService");
                if (IPorPORT.equals("IP")){
                    return US.getString("ip");
                } else {
                    return Integer.toString(US.getInt("port"));
                }
            } else if (UPO == 'P'){
                JSONObject PS = jsonObject.getJSONObject("ProductService");
                if (IPorPORT.equals("IP")){
                    return PS.getString("ip");
                } else {
                    return Integer.toString(PS.getInt("port"));
                }
            } else if (UPO == 'O'){
                JSONObject OS = jsonObject.getJSONObject("OrderService");
                if (IPorPORT.equals("IP")){
                    return OS.getString("ip");
                } else {
                    return Integer.toString(OS.getInt("port"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
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
