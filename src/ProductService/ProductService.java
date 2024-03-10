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

        // Initialize filepaths
        String backupFilePath = System.getProperty("user.dir") + "/compiled/ProductService/product_backup.json";
        String filePath = System.getProperty("user.dir") + "/compiled/ProductService/product_database.json";

        // Relay data to the backup database (if the most recent command was shutdown)
        try
        {
            // Read our database, and fill it into a JSONArray
            FileReader fileReader = new FileReader(filePath);
            JSONTokener tokener = new JSONTokener(fileReader);
            JSONArray jsonArray = new JSONArray(tokener);

            // Check if a shutdown request has been received within the database files
            for (int i = 0; i < jsonArray.length(); i++)
            {
                if (jsonArray.getJSONObject(i).get("command").equals("shutdown"))
                {
                    // Write the JSON array to the backup file
                    try (FileWriter fileWriter = new FileWriter(backupFilePath)) {
                        fileWriter.write(jsonArray.toString());
                    }
                }
            }
            fileReader.close();
            tokener.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error creating product backup.");
        }


        // Reset product database
        try {
            // Clear product_database.json
            FileWriter fileWriter = new FileWriter(filePath);
            fileWriter.write("[]");
            fileWriter.close();

        }

        catch (IOException e)
        {
            e.printStackTrace();
            System.err.println("Error clearing ProductService.json");
        }


        int port = Integer.parseInt(getIPorPORT("PORT", 'P'));
        String ip = getIPorPORT("IP", 'P');
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
                        case "restart":
                            restart(exchange, jsonObject); break;
                        case "shutdown":
                            shutdown(exchange, jsonObject); break;
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
     * Handles the restart operation by reading data from the backup file and restoring it to the main database.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param jsonObject The JSON data containing information about the restart operation.
     * @throws IOException If an I/O error occurs while handling the request.
     */
    private static void restart(HttpExchange exchange, JSONObject jsonObject) throws IOException
    {
        //Initialize variables
        String backupFilePath = System.getProperty("user.dir") + "/compiled/ProductService/product_backup.json";
        String filePath = System.getProperty("user.dir") + "/compiled/ProductService/product_database.json";
        boolean validRestart = false;

        try
        {
            // Read our backup database, and fill it into a JSONArray
            FileReader fileReader = new FileReader(backupFilePath);
            JSONTokener tokener = new JSONTokener(fileReader);
            JSONArray jsonArray = new JSONArray(tokener);

            // Check if a shutdown request has been received within the backup files
            for (int i = 0; i < jsonArray.length(); i++)
            {
                if (jsonArray.getJSONObject(i).get("command").equals("shutdown"))
                {
                    try (FileWriter fileWriter = new FileWriter(filePath))
                    {
                        // remove the shutdown command
                        jsonArray.remove(i);

                        // reboot the old data from the backup file
                        fileWriter.write(jsonArray.toString());

                        // Reset our backup database
                        try (FileWriter fileWriter2 = new FileWriter(backupFilePath))
                        {
                            fileWriter2.write("[]");
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }

                        sendResponse(exchange, 200, jsonObject.toString());
                        validRestart = true;
                    }
                    catch (Exception e)
                    {
                        System.out.println("Error writing to " + filePath);
                        sendResponse(exchange, 400, new JSONObject().toString());
                    }
                }
            }

            if(!validRestart)
            {
                // Trying to restart without any previous shutdown data results in an error
                sendResponse(exchange, 400, new JSONObject().toString());
            }

            fileReader.close();
            tokener.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //If any weird error occurs, then ProductService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }

        exchange.close();
    }

    /**
     * Handles the shutdown operation by adding the provided JSON data to the backup file.
     * Exits the application after completing the shutdown process.
     *
     * @param exchange The HttpExchange object representing the HTTP request and response.
     * @param jsonObject The JSON data containing information about the shutdown operation.
     * @throws IOException If an I/O error occurs while handling the request.
     */
    private static void shutdown(HttpExchange exchange, JSONObject jsonObject) throws IOException{
        try
        {
            // Send shutdown command to the backup file
            String filePath = System.getProperty("user.dir") + "/compiled/ProductService/product_database.json";

            // Read our JSON file database, and fill it into a JSONArray
            FileReader fileReader = new FileReader(filePath);
            JSONTokener tokener = new JSONTokener(fileReader);
            JSONArray jsonArray = new JSONArray(tokener);

            // Add our new json to the JSONArray
            jsonArray.put(jsonObject);

            // Write the entire JSONArray to the file
            try (FileWriter fileWriter = new FileWriter(filePath))
            {
                jsonArray.write(fileWriter);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            String response = jsonObject.toString();

            // Respond with status code 200 for a valid creation
            sendResponse(exchange, 200, response);

            fileReader.close();
            tokener.close();
            System.exit(0);
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

    /**
     * Retrieves the IP address or port number from the configuration file based on the specified type.
     *
     * @param IPorPORT Specifies whether to retrieve the IP address ("IP") or port number ("PORT").
     * @param UPO Indicates the type of service ('U' for UserService, 'P' for ProductService, 'O' for OrderService).
     * @return The IP address or port number as a String.
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
}
