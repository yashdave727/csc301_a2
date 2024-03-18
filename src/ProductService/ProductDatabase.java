import java.sql.*;
import redis.clients.jedis.Jedis;


/**
 * UserDatabase class provides methods for managing user data in a SQLite database.
 */
public class ProductDatabase {
    public static String url = "jdbc:postgresql://142.1.44.57:5432/assignmentdb";
    private final String user = "assignmentuser";
    private final String password = "assignmentpassword";
    private final String redisHost = "localhost"; // Change this to your Redis server's IP address
    private final int redisPort = 6379;

    /**
     * The connect method is used to establish a connection to the database.
     * @return value is a connection object to the SQLite database.
     */
    private Connection connect() {
        Connection con = null;
        try {
	    // TODO: Add REDIS connection
            con = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return con;
    }
    
    private Jedis connectToRedis() {
        try {
            Jedis jedis = new Jedis(redisHost, redisPort);
            return jedis; // Successfully connected
        } catch (Exception e) {
            System.out.println("Failed to connect to Redis: " + e.getMessage());
            return null; // Connection failed
        }
    }

    public void storeInRedis(String key, String json) {
        Jedis jedis = connectToRedis(); 
        if (jedis != null) {
            jedis.set(key, json);
            jedis.close();
        }
    }
    
    public String retrieveFromRedis(String key) {
        Jedis jedis = connectToRedis(); 
        if (jedis != null) {
            String value = jedis.get(key);
            jedis.close();
            return value;
        }
        return null;
    }
    
    public void invalidateInRedis(String key) {
        Jedis jedis = connectToRedis(); 
        if (jedis != null) {
            jedis.del(key);
            jedis.close();
        }
    }
    
    

    /**
     * The initialize method which is used in the constructor is for initializing the database by creating a table
     * for users if it does not already exist.
     * @param dockerIp is the IP address of the Docker container running the database.
     * @param dbPort is the port number of the database.
     * @param redisPort is the port number of the Redis server.
     */
    public void initialize(String dockerIp, String dbPort, String redisPort) {
	url = "jdbc:postgresql://" + dockerIp + ":" + dbPort + "/assignmentdb";
        try (Connection con = connect();
             Statement statement = con.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS products (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "price REAL NOT NULL," +
                    "quantity INTEGER NOT NULL)";
            statement.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Retrieves a user's information from the database based on the user ID.
     *
     * @param id is the ID of the user to retrieve.
     * @return A JSON string containing the user's information, or an empty string if not found.
     */
    public String getProduct(int id) {
        // Attempt to retrieve from Redis first
        String cachedProduct = retrieveFromRedis("product:" + id);
        if (cachedProduct != null) {
            return cachedProduct;
        }
    
        // If not in cache, retrieve from database
        String sql = "SELECT id, name, description, price, quantity FROM products WHERE id = ?";
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                String productJson = String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}",
                    rs.getInt("id"), rs.getString("name"), rs.getString("description"), rs.getFloat("price"), rs.getInt("quantity"));
                // Store in Redis for future requests
                storeInRedis("product:" + id, productJson);
                return productJson;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return "";
    }
    


    public int createProduct(int id, String name, String description, float price, int quantity) {
        String sql = "INSERT INTO products(id, name, description, price, quantity) VALUES(?, ?, ?, ?, ?)";
        
        // Check if the price or quantity is a negative value and return 400 for bad request.
        if (price < 0 || quantity < 0) {
            return 400;
        }
        
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setFloat(4, price);
            statement.setInt(5, quantity);
            int result = statement.executeUpdate();
            
            if (result > 0) {
                String productJson = String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}",
                                                    id, name, description, price, quantity);
                storeInRedis("product:" + id, productJson);
                return 200;  // OK - Product created successfully
            }
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) {
                return 409;  // Duplicate entry
            } else {
                return 400;  // Internal Server Error
            }
        }
        return 400;  // Default error if insertion failed
    }
    

    
    public int deleteProduct(int id, String name, float price, int quantity) {
        String sql = "DELETE FROM products WHERE id = ? AND name = ? AND price = ? AND" +
                " quantity = ?";

        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.setFloat(3, price);
            statement.setInt(4, quantity);
            int affectedRows = statement.executeUpdate();

            // Product had been deleted if number of rows has changed
            if (affectedRows > 0) {
                // After deleting from the database, also remove from Redis if it's cached
                invalidateInRedis("product:" + id);
                return 200;
            }
            // As specified in Piazza post @127
            else {
                return 404;
            }
        }
        catch (SQLException e) {
            return 400; // Internal Server Error
        }
    }



    public int updateProduct(int id, String name, String description, float price, int quantity) {
        StringBuilder sqlUpdate = new StringBuilder("UPDATE products SET ");
        int valueCount = 0;
    
        if ((price != 0 && price < 0) || (quantity != 0 && quantity < 0)) {
            return 400;  // Bad request due to negative price or quantity
        }
    
        // Construct the SQL update statement based on provided values
        if (name != null) {
            sqlUpdate.append("name = ?, ");
            valueCount++;
        }
        if (description != null) {
            sqlUpdate.append("description = ?, ");
            valueCount++;
        }
        if (price != 0) {
            sqlUpdate.append("price = ?, ");
            valueCount++;
        }
        if (quantity != 0) {
            sqlUpdate.append("quantity = ?, ");
            valueCount++;
        }
    
        if (valueCount == 0) {
            return 200;  // No update was needed
        }
        
        sqlUpdate.delete(sqlUpdate.length() - 2, sqlUpdate.length());  // Remove trailing comma and space
        sqlUpdate.append(" WHERE id = ?");
        
        try (Connection conn = this.connect();
             PreparedStatement statement = conn.prepareStatement(sqlUpdate.toString())) {
            int valueIndex = 1;
    
            // Set values for the update statement
            if (name != null) {
                statement.setString(valueIndex++, name);
            }
            if (description != null) {
                statement.setString(valueIndex++, description);
            }
            if (price != 0) {
                statement.setFloat(valueIndex++, price);
            }
            if (quantity != 0) {
                statement.setInt(valueIndex++, quantity);
            }
            statement.setInt(valueIndex, id);
    
            int affectedRows = statement.executeUpdate();
    
            if (affectedRows > 0) {
                // After updating the database, update Redis if the product is cached
                String newProductJson = String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}",
                                                       id, name != null ? name : "", description != null ? description : "", price, quantity);
                storeInRedis("product:" + id, newProductJson);
                return 200;
            } else {
                return 404;  // Product not found
            }
        } catch (SQLException e) {
            return 400;  // Internal Server Error
        }
    }
    


}
