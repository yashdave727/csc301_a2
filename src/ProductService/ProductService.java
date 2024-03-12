import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.InetSocketAddress;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * This is the microservice that handles any data related to products.
 * The Product Server accepts HTTPRequests and returns a response back to the client.
 */
public class ProductService
{
    static final ProductDatabase productDB = new ProductDatabase();
    /**
     * The main entry point for the ProductService application.
     * Initializes the server, sets up HTTP request handlers, and starts the server.
     * Reads configuration parameters, handles shutdown requests, and resets the product database if needed.
     *
     * @param args An array of command-line arguments. The first argument is the absolute path to the working directory.
     * @throws IOException If an I/O error occurs while reading or writing files.
     */
    public static void main(String[] args) throws IOException
    {
        String ip = "0.0.0.0";
        int port = -1;

        // Get port from the command line
        if (args.length > 0)
        {
            port = Integer.parseInt(args[0]);
        }
        else
        {
            System.out.println("No command-line arguments provided.");
            System.exit(1);
        }


        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed

        // Set up context for a POST request
        server.createContext("/product", new PostHandler());

        // Set up context for a GET request
        server.createContext("/product/", new GetHandler());


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);
    }

    /**
     * Handles POST requests for ProductService, parsing the incoming JSON data and directing it to specific operations.
     * Implements the HttpHandler interface to handle HTTP exchanges.
     */
    static class PostHandler implements HttpHandler
    {
        /**
         * Handles the incoming HTTP exchange for POST requests, parsing the JSON data and directing it to specific operations.
         *
         * @param exchange The HttpExchange object representing the HTTP request and response.
         * @throws IOException If an I/O error occurs while handling the request.
         */
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                if ("POST".equals(exchange.getRequestMethod()))
                {
                    //Initialize variables
                    String productData = getRequestBody(exchange);
                    JSONObject jsonObject = new JSONObject(productData);

                    // Parse the command
                    String command = jsonObject.getString("command");

                    switch (command)
                    {
                        case "create":
                            create(exchange, jsonObject); break;
                        case "update":
                            update(exchange, jsonObject); break;
                        case "delete":
                            delete(exchange, jsonObject); break;
                        default:
                            sendResponse(exchange, 400, new JSONObject().toString()); break;
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                //If any weird error occurs, then ProductService has received a bad http request
                sendResponse(exchange, 400, new JSONObject().toString());
            }
            exchange.close();
        }
    }

    /**
     * Handles the creation of a new product based on the provided JSON data.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param jsonObject The JSON data containing information about the product to be created.
     * @throws IOException If an I/O error occurs while handling the request.
     */
    private static void create(HttpExchange exchange, JSONObject jsonObject) throws IOException {
        try
        {
            int id, quantity;
            float price;
            String name, description;
            JSONObject responseBody = new JSONObject();

            if (jsonObject.has("id") && jsonObject.has("name") && jsonObject.has("description")
                    && jsonObject.has("price") && jsonObject.has("quantity")) {

                id = jsonObject.getInt("id");
                name = jsonObject.getString("name");
                description = jsonObject.getString("description");
                price = jsonObject.getFloat("price");
                quantity = jsonObject.getInt("quantity");

                int createStatus = productDB.createProduct(id, name, description, price, quantity);
                if (createStatus == 200) {
                    responseBody.put("id", id);
                    responseBody.put("name", name);
                    responseBody.put("description", description);
                    responseBody.put("price", price);
                    responseBody.put("quantity", quantity);
                    sendResponse(exchange, createStatus, responseBody.toString());
                } else {
                    sendResponse(exchange, createStatus, new JSONObject().toString());
                }
            }
            // The fields provided are invalid
            else {
                sendResponse(exchange,400, new JSONObject().toString());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //If any weird error occurs, then ProductService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
    }

    /**
     * Handles the update of an existing product based on the provided JSON data.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param jsonObject The JSON data containing information about the product to be updated.
     * @throws IOException If an I/O error occurs while handling the request.
     */
    private static void update(HttpExchange exchange, JSONObject jsonObject) throws IOException {
        try
        {
            int id, quantity;
            float price;
            String name, description;

            // command and ID are the only required fields.
            if (jsonObject.has("id")) {
                id = jsonObject.getInt("id");
                // Fields below are optional so provide null as the default value.
                name = jsonObject.optString("name", null);
                description = jsonObject.optString("description", null);
                price = jsonObject.has("price") ? (float) jsonObject.getDouble("price") : 0;
                quantity = jsonObject.has("quantity") ? jsonObject.getInt("quantity") : 0;

                int updateStatus = productDB.updateProduct(id, name, description, price, quantity);
                if (updateStatus == 200) {
                    // Retrieve updated product data to include in the response.
                    String productData = productDB.getProduct(id);
                    sendResponse(exchange, updateStatus, new JSONObject(productData).toString());
                } else {
                    sendResponse(exchange, updateStatus, new JSONObject().toString());
                }
            } else {
                sendResponse(exchange, 400, new JSONObject().toString());
            }
        }
        catch (Exception e)
        {
            //If any weird error occurs, then ProductService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
    }

    /**
     * Handles the deletion of an existing product based on the provided JSON data.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param jsonObject The JSON data containing information about the product to be deleted.
     * @throws IOException If an I/O error occurs while handling the request.
     */
    private static void delete(HttpExchange exchange, JSONObject jsonObject) throws IOException {
        try
        {
            int id, quantity;
            float price;
            String name, description;
            // All the fields are required
            if (jsonObject.has("id") && jsonObject.has("name") && jsonObject.has("description")
                    && jsonObject.has("price") && jsonObject.has("quantity")) {

                id = jsonObject.getInt("id");
                name = jsonObject.getString("name");
                description = jsonObject.getString("description");
                price = jsonObject.getFloat("price");
                quantity = jsonObject.getInt("quantity");

                int deleteStatus = productDB.deleteProduct(id, name, description, price, quantity);
                // The deletion is valid, and return empty response with status code 200.
                if (deleteStatus == 200) {
                    sendResponse(exchange, deleteStatus, new JSONObject().toString());
                }
                else {
                    sendResponse(exchange, deleteStatus, new JSONObject().toString());
                }
            }
            // The fields provided are invalid
            else {
                sendResponse(exchange, 400, new JSONObject().toString());
            }
        }
        catch (Exception e)
        {
            //If any weird error occurs, then ProductService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
    }

    /**
     * Handles GET requests for product information, specifically retrieving details based on product ID.
     * This class is responsible for processing GET requests to the /product endpoint.
     */
    static class GetHandler implements HttpHandler
    {
        /**
         * Handles the processing of GET requests for product information.
         *
         * @param exchange The HttpExchange object representing the HTTP request and response.
         * @throws IOException If an I/O error occurs while handling the request.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");
            if (pathParts.length != 3 || !pathParts[1].equals("product")) {
                sendResponse(exchange, 400, new JSONObject().toString());
                return;
            }
            try {
                int productId = Integer.parseInt(pathParts[2]);
                String productData = productDB.getProduct(productId);
                if (productData.isEmpty()) {
                    sendResponse(exchange, 404, new JSONObject().toString());
                } else {
                    // Valid response, which returns product's data: id, name, description, price, quantity
                    sendResponse(exchange, 200, new JSONObject(productData).toString());
                }
            }
            catch (Exception e)
            {
                //If any weird error occurs, then ProductService has received a bad http request
                sendResponse(exchange, 400, new JSONObject().toString());
            }
            exchange.close();
        }
    }

    /**
     * Sends an HTTP response to the client with the specified status code and response body.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param rCode The HTTP status code to be sent in the response.
     * @param response The response body to be sent in the response.
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
     * Reads and retrieves the request body from an HTTP exchange.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @return The content of the request body as a String.
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
