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
     * @param args Command-line arguments. The first argument is the port number to listen on. The second argument is the IP address of the Docker container running the database. The third argument is the port number of the database. The fourth argument is the port number of the Redis server.
     * @throws IOException If an I/O error occurs during the initialization or execution of the server.
     */
    public static void main(String[] args) throws IOException
    {
        String ip = "0.0.0.0";
	String dockerIp;
        int port, dbPort, redisPort;

        // Get port to listen on
	// Get docker ip
	// Get db port
	// Get redis port
	if (args.length != 4)
        {
            System.out.println("Missing arguments <port> <dockerIp> <dbPort> <redisPort>");
            System.exit(1);
        }

        port = Integer.parseInt(args[0]);
	dockerIp = args[1];
	dbPort = Integer.parseInt(args[2]);
	redisPort = Integer.parseInt(args[3]);

        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed

        // Set up context for a POST request
        server.createContext("/user", new PostHandler());

        // Set up context for a GET request
        server.createContext("/user/", new GetHandler());

        server.setExecutor(null); // creates a default executor

        server.start();

        //("Server started on port " + port);

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
                        default:
			    //("HELLO 103");
                            sendResponse(exchange, 400, new JSONObject().toString()); break;
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
		//("HELLO 111");
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
                //("HELLO 130");
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
            responseBody.put("password", UserDatabase.hashPassword(password));

            if (statusCode == 200) {
                sendResponse(exchange, 200, responseBody.toString());
            } else {
                sendResponse(exchange, 409, new JSONObject().toString());
            }
        } catch (Exception e) {
            // e.printStackTrace();
            //("HELLO 158");
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
		//("HELLO (In Order Service Update)");
                sendResponse(exchange, 400, new JSONObject().toString());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
	    //("HELLO (In Order Service Update, it errors)");
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
		//("HELLO (In Order Service Delete, invalid fields)");
                sendResponse(exchange, 400, new JSONObject().toString());
            }
        }
        catch (Exception e)
        {
	    //("HELLO (In Order Service delete, it errors)");
            //If any weird error occurs, then UserService has received a bad http request
            sendResponse(exchange, 400, new JSONObject().toString());
        }
        exchange.close();
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
}
