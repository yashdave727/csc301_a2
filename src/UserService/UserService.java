import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This is the microservice that handles any data related to products.
 * The Product Server accepts HTTPRequests and returns a response back to the client.
 */
public class UserService
{
    static final UserDatabase userDB = new UserDatabase();
    /**
     * The main method for the UserService application. Starts an HTTP server to handle user-related requests.
     *
     * @param args Command-line arguments. Expects an absolute working directory path as the first argument.
     * @throws IOException If an I/O error occurs during the initialization or execution of the server.
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
        String backupFilePath = System.getProperty("user.dir") + "/compiled/UserService/user_backup.json";
        String filePath = System.getProperty("user.dir") + "/compiled/UserService/user_database.json";

        // Relay data to the backup database (if the most recent command was shutdown)
        try
        {
            // Read our database, and fill it into a JSONArray
            FileReader fileReader = new FileReader(filePath);
            JSONTokener tokener = new JSONTokener(fileReader);
            JSONArray jsonArray = new JSONArray(tokener);

            // Check if a shutdown request has been received within the backup files
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
            System.err.println("Error creating user backup.");
        }


        // Reset user database
        try {
            // Clear UserService.json
            FileWriter fileWriter = new FileWriter(filePath);
            fileWriter.write("[]");
            fileWriter.close();

        } catch (IOException e) {
            System.err.println("Error clearing UserService.json");
        }


        int port = Integer.parseInt(getIPorPORT("PORT", 'U'));
        String ip = getIPorPORT("IP", 'U');
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed

        // Set up context for a POST request
        server.createContext("/user", new PostHandler());

        // Set up context for a GET request
        server.createContext("/user/", new GetHandler());

        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("Server started on port " + port);

    }

    /**
     * Handles HTTP POST requests for the UserService application.
     * Parses the request body into a JSONObject and performs actions based on the specified command.
     */
    static class PostHandler implements HttpHandler
    {
        /**
         * Handles HTTP POST requests by parsing the request body into a JSONObject
         * and invoking corresponding actions based on the specified command.
         *
         * @param exchange The HTTP exchange object representing the client-server communication.
         * @throws IOException If an I/O error occurs during the handling of the HTTP request.
         */
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                if ("POST".equals(exchange.getRequestMethod()))
                {
                    //Initialize variables
                    String userData = getRequestBody(exchange);
                    JSONObject jsonObject = new JSONObject(userData);

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
                //If any weird error occurs, then UserService has received a bad http request
                sendResponse(exchange, 400, new JSONObject().toString());
            }
            exchange.close();
        }
    }

    /**
     * Creates a new user based on the provided JSON data.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
     * @param jsonObject The JSONObject containing user data.
     * @throws IOException If an I/O error occurs during the user creation process.
     */
    private static void create(HttpExchange exchange, JSONObject jsonObject) throws IOException {
        try {
            // Check if all required fields are present, including the ID
            if (!jsonObject.has("id") || !jsonObject.has("username") || !jsonObject.has("email") || !jsonObject.has("password")) {
                sendResponse(exchange, 400, new JSONObject().toString());
                return;
            }

            JSONObject responseBody = new JSONObject();

            // Extracting data from JSON object
            int id = jsonObject.getInt("id");
            String username = jsonObject.getString("username");
            String email = jsonObject.getString("email");
            String password = jsonObject.getString("password"); // Consider hashing

            // Attempt to create a new user in the database, passing the ID
            int statusCode = userDB.createUser(id, username, email, password);

            responseBody.put("id", id);
            responseBody.put("username", username);
            responseBody.put("email", email);
            responseBody.put("password", password);

            if (statusCode == 200) {
                sendResponse(exchange, 200, responseBody.toString());
            } else {
                sendResponse(exchange, 409, new JSONObject().toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 400, new JSONObject().toString());
        }
    }


    /**
     * Updates an existing user based on the provided JSON data.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
     * @param jsonObject The JSONObject containing updated user data.
     * @throws IOException If an I/O error occurs during the user update process.
     */
    private static void update(HttpExchange exchange, JSONObject jsonObject) throws IOException
    {
        try
        {
            int id;
            String username, email, password;
            if (jsonObject.has("id")) {
                id = jsonObject.getInt("id");
                // Fields below are optional so defaultValue is null.
                username = jsonObject.optString("username", null);
                email = jsonObject.optString("email", null);
                password = jsonObject.optString("password", null);

                int updateStatus = userDB.updateUser(id, username, email, password);
                if (updateStatus == 200) {
                    // Use getUser() to retrieve user data along with the hashed password.
                    String userData = userDB.getUser(id);
                    sendResponse(exchange, updateStatus, new JSONObject(userData).toString());

                } else {
                    sendResponse(exchange, updateStatus, new JSONObject().toString());
                }
            } else {
                sendResponse(exchange, 400, new JSONObject().toString());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //If any weird error occurs, then UserService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
    }

    /**
     * Deletes an existing user based on the provided JSON data.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
     * @param jsonObject The JSONObject containing user data for deletion.
     * @throws IOException If an I/O error occurs during the user deletion process.
     */
    private static void delete(HttpExchange exchange, JSONObject jsonObject) throws IOException
    {
        try
        {
            int id;
            String username, email, password;
            // All the fields are required
            if (jsonObject.has("id") && jsonObject.has("username") && jsonObject.has("email") && jsonObject.has("password")) {
                id = jsonObject.getInt("id");
                username = jsonObject.getString("username");
                email = jsonObject.getString("email");
                password = jsonObject.getString("password");
                int deleteStatus = userDB.deleteUser(id, username, email, password);
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
            //If any weird error occurs, then UserService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
        exchange.close();
    }

    /**
     * Restarts the UserService based on the provided JSON data.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
     * @param jsonObject The JSONObject containing restart data.
     * @throws IOException If an I/O error occurs during the restart process.
     */
    private static void restart(HttpExchange exchange, JSONObject jsonObject) throws IOException
    {
        //Initialize variables
        String backupFilePath = System.getProperty("user.dir") + "/compiled/UserService/user_backup.json";
        String filePath = System.getProperty("user.dir") + "/compiled/UserService/user_database.json";
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
            //If any weird error occurs, then UserService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }

        exchange.close();
    }

    /**
     * Initiates a shutdown of the UserService based on the provided JSON data.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
     * @param jsonObject The JSONObject containing shutdown data.
     * @throws IOException If an I/O error occurs during the shutdown process.
     */
    private static void shutdown(HttpExchange exchange, JSONObject jsonObject) throws IOException{
        try
        {
            // Send shutdown command to the backup file
            String filePath = System.getProperty("user.dir") + "/compiled/UserService/user_database.json";

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
            //If any weird error occurs, then UserService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
    }

    /**
     * Handles GET requests for user-related data in the UserService.
     */
    static class GetHandler implements HttpHandler
    {
        /**
         * Handles GET requests for user-related data in the UserService.
         * The method processes incoming GET requests and retrieves user data based on the provided user ID.
         * It searches the user database and responds with the requested user's information if found.
         * If the requested user ID does not exist, it returns a 404 status code. Non-GET requests receive a 405 status code.
         * Any unexpected errors during the processing result in a 400 status code.
         *
         * @param exchange The HTTP exchange object representing the client-server communication.
         * @throws IOException If an I/O error occurs during the handling of the GET request.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");
            if (pathParts.length != 3 || !pathParts[1].equals("user")) {
                // Bad request
                sendResponse(exchange, 400, new JSONObject().toString());
                return;
            }
            try {
                int userId = Integer.parseInt(pathParts[2]);
                String userData = userDB.getUser(userId);
                if (userData.isEmpty()) {
                    // User is not found - 404
                    sendResponse(exchange, 404, new JSONObject().toString());
                }
                else {
                    // Valid response, which returns user's data - id, username, email, hashed password
                    sendResponse(exchange, 200, new JSONObject(userData).toString());
                }
            }
            catch (NumberFormatException e) {
                // Invalid user ID format - can only be integer
                sendResponse(exchange, 400, new JSONObject().toString());
            }
        }
    }

    /**
     * Calculates the SHA-256 hash for a given input string.
     *
     * @param input The string for which the SHA-256 hash needs to be calculated.
     * @return The SHA-256 hash as a hexadecimal string.
     * @throws NoSuchAlgorithmException If the specified cryptographic algorithm is not available.
     */
    private static String calculateHash(String input) throws NoSuchAlgorithmException {
        // Create a SHA-256 MessageDigest instance
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Convert the input string to bytes and update the digest
        byte[] hashedBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        // Convert the byte array to a hexadecimal string
        StringBuilder hexString = new StringBuilder();
        for (byte hashedByte : hashedBytes) {
            String hex = Integer.toHexString(0xff & hashedByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        // Return the SHA-256 hash as a string
        return hexString.toString();
    }

    /**
     * Sends an HTTP response with the specified status code and response content.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
     * @param rCode The HTTP status code for the response.
     * @param response The content of the response to be sent.
     * @throws IOException If an I/O error occurs during the response sending process.
     */
    public static void sendResponse(HttpExchange exchange, int rCode, String response) throws IOException {
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
     * Retrieves the request body from an HTTP exchange.
     *
     * @param exchange The HTTP exchange object representing the client-server communication.
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
     * Retrieves the IP address or port number based on the specified category and user type.
     *
     * @param IPorPORT The category, either "IP" or "PORT," for which to retrieve the information.
     * @param UPO The user type identifier ('U' for UserService, 'P' for ProductService, 'O' for OrderService).
     * @return The IP address or port number as a string.
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
