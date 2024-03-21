import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

import redis.clients.jedis.Jedis;

/**
 * UserDatabase class provides methods for managing user data in a SQLite database.
 */

class UserDatabase {

    public static String url = "jdbc:postgresql://142.1.44.57:5432/assignmentdb";
    private final String user = "assignmentuser";
    private final String password = "assignmentpassword";
    public static String redisHost = "localhost";  // Change to your Redis host IP
    public static int redisPort = 6379;
    public static HikariDataSource dataSource;

//    static {
//        // Configure HikariCP
//        HikariConfig config = new HikariConfig();
//        // Adjust the JDBC URL, username, and password to match your PostgreSQL container setup
//        config.setJdbcUrl("jdbc:postgresql://142.1.44.57:5432/assignmentdb");
//        config.setUsername("assignmentuser");
//        config.setPassword("assignmentpassword");
//
//        // // Optional: Configure additional HikariCP settings as needed
//        // config.addDataSourceProperty("cachePrepStmts", "true");
//        // config.addDataSourceProperty("prepStmtCacheSize", "250");
//        // config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
//
//        dataSource = new HikariDataSource(config);
//    }

    /**
     * The connect method is used to establish a connection to the database.
     * @return value is a connection object to the SQLite database.
     */
    private Connection connect() throws SQLException {
        return dataSource.getConnection();
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
        Jedis jedis = connectToRedis(); // Adjust host and port if necessary
        if (jedis != null) {
            jedis.set(key, json);
            jedis.close();
        }
    }

    public String retrieveFromRedis(String key) {
        Jedis jedis = connectToRedis(); // Adjust host and port if necessary
        if (jedis != null) {
            String value = jedis.get(key);
            jedis.close();
            return value;
        }
        return null;
    }

    public void invalidateInRedis(String key) {
        Jedis jedis = connectToRedis(); // Adjust host and port if necessary
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
    public void initialize(String dockerIp, String dbPort, String _redisPort) {
	url = "jdbc:postgresql://" + dockerIp + ":" + dbPort + "/assignmentdb";
	redisPort = Integer.parseInt(_redisPort);
	redisHost = dockerIp;

	// Configure HikariCP
	HikariConfig config = new HikariConfig();
	config.setJdbcUrl(url);
	config.setUsername(user);
	config.setPassword(password);


	dataSource = new HikariDataSource(config);

        try (Connection con = connect();
             Statement statement = con.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY," +
                    "username TEXT NOT NULL," +
                    "email TEXT NOT NULL," +
                    "password TEXT NOT NULL," +
                    "deleted BOOLEAN NOT NULL DEFAULT FALSE)";
            statement.execute(sql);
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Product Database connection pool successfully shut down.");
        }
    }

    /**
     * Creates a new user in the database.
     * @param id is the ID of the user.
     * @param username is the username of the user.
     * @param email is the email address of the user.
     * @param password is the password of the user.
     * @return An HTTP status code representing the result of the operation.
     */
    public int createUser(int id, String username, String email, String password) {
        String sql = "INSERT INTO users(id, username, email, password) VALUES(?, ?, ?, ?)";
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, username);
            statement.setString(3, email);
            statement.setString(4, hashPassword(password));
            statement.executeUpdate();

            // Cache the new user data in Redis
            String userJson = String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}",
                                             id, username, email, hashPassword(password));
            storeInRedis("user:" + id, userJson);

            return 200; // OK - User created successfully
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) {
                return 409; // Duplicate entry
            } else {
                return 400; // Internal Server Error
            }
        }
    }


    /**
     * Retrieves a user's information from the database based on the user ID.
     * @param id is the ID of the user to retrieve.
     * @return A JSON string containing the user's information, or an empty string if not found.
     */
    public String getUser(int id) {
        // Attempt to retrieve from Redis first
        String cachedUser = retrieveFromRedis("user:" + id);
        if (cachedUser != null) {
            return cachedUser;
        }

        // If not in cache, retrieve from database
        String sql = "SELECT id, username, email, password FROM users WHERE id = ?";
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                String userJson = String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}",
                                                 rs.getInt("id"), rs.getString("username"), rs.getString("email"), hashPassword(rs.getString("password")));
                // Store in Redis for future requests
                storeInRedis("user:" + id, userJson);
                return userJson;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return "";
    }

    public int deleteUser(int id, String username, String email, String password) {
        String sql = "UPDATE users SET deleted = TRUE WHERE id = ? AND username = ? AND email = ? AND password = ?";

        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, username);
            statement.setString(3, email);
            statement.setString(4, password);
            int affectedRows = statement.executeUpdate();

            // User had been updated if any of the columns' values have changed
            if (affectedRows > 0) {
                invalidateInRedis("user:" + id);
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


    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hashedBytes);
            StringBuilder hexString = new StringBuilder(number.toString(16));
            while (hexString.length() < 32) {
                hexString.insert(0, '0');
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not hash password", e);
        }
    }

    public int updateUser(int id, String username, String email, String password) {
        StringBuilder sqlUpdate = new StringBuilder("UPDATE users SET ");
        boolean fieldAdded = false;

        if (username != null && !username.isEmpty()) {
            sqlUpdate.append("username = ?, ");
            fieldAdded = true;
        }
        if (email != null && !email.isEmpty()) {
            sqlUpdate.append("email = ?, ");
            fieldAdded = true;
        }
        if (password != null && !password.isEmpty()) {
            sqlUpdate.append("password = ?, ");
            fieldAdded = true;
        }

        if (!fieldAdded) {
            return 200; // No update needed
        }

        sqlUpdate.delete(sqlUpdate.length() - 2, sqlUpdate.length()); // Remove last comma and space
        sqlUpdate.append(" WHERE id = ?");

        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sqlUpdate.toString())) {
            int index = 1;
            if (username != null && !username.isEmpty()) {
                statement.setString(index++, username);
            }
            if (email != null && !email.isEmpty()) {
                statement.setString(index++, email);
            }
            if (password != null && !password.isEmpty()) {
                statement.setString(index++, hashPassword(password));
            }
            statement.setInt(index, id);

            int affectedRows = statement.executeUpdate();
            if (affectedRows > 0) {
                // If update was successful, update Redis cache
                String updatedUserJson = String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}",
                                                        id, username, email, hashPassword(password));
                storeInRedis("user:" + id, updatedUserJson);
                return 200;
            } else {
                return 404; // User not found
            }
        } catch (SQLException e) {
            return 500; // Internal Server Error
        }
    }

}
