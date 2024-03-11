import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

/**
 * OrderDatabase class provides methods for managing user data in a SQLite database.
 */

class OrderDatabase {

    private static final String url = "jdbc:postgresql://localhost:5435/assignmentdb";
    private static final String user = "assignmentuser";
    private static final String password = "assignmentpassword";


    /**
     * Constructor for the UserDatabase. This method initializes the database using the initialize().
     */
    public OrderDatabase() {
        initialize();
    }

    /**
     * The connect method is used to establish a connection to the SQLite database.
     * @return value is a connection object to the SQLite database.
     */
    private Connection connect() {
        Connection con = null;
        try {
            con = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return con;
    }

    /**
     * The initialize method which is used in the constructor is for initializing the database by creating a table
     * for users if it does not already exist.
     */
    public void initialize() {
        try (Connection con = connect();
            Statement statement = con.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS orders (" +
                    "id SERIAL PRIMARY KEY, " +
                    "user_id INT NOT NULL, " +
                    "prod_id INT NOT NULL, " +
                    "quantity INT NOT NULL CHECK (quantity > 0), " +
                    "deleted BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) " +
                    "ON DELETE CASCADE, " +
                    "FOREIGN KEY (prod_id) REFERENCES products(id) " +
                    "ON DELETE CASCADE)";
            statement.execute(sql);
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Creates a new user in the database.
     * @param id is the ID of the user.
     * @param user_id is the ID of the user making the order.
     * @param prod_id is the ID of the product being bought.
     * @param quantity is the quantity of the bought product.
     * @return An HTTP status code representing the result of the operation.
     */
    public int placeOrder(int id, int user_id, int prod_id, int quantity) {
        String sql = "INSERT INTO orders(id, user_id, prod_id, quantity) VALUES(?, ?, ?, ?)";
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, user_id);
            statement.setInt(3, prod_id);
            statement.setInt(4, quantity);
            statement.executeUpdate();
            return 200; // OK - User created successfully
        }
        // The PostgreSQL 23505 UNIQUE VIOLATION error occurs when a unique constraint is violated. See the link below
        // https://www.metisdata.io/knowledgebase/errors/postgresql-23505#:~:text=The%20PostgreSQL%
        // 2023505%20UNIQUE%20VIOLATION,fail%20to%20complete%20the%20operation.
        catch (SQLException e) {
            if (e.getSQLState().equals("23505")) {
                return 409; // Duplicate entry
            }
            else {
                return 500; // Internal Server Error
            }
        }
    }

    /**
     * Retrieves a user's information from the database based on the user ID.
     * @param id is the ID of the user.
     * @return A JSON string containing the products ID as a key and quantity as a value
     */
    public String getPurchased(int user_id) {
        String sql = "SELECT prod_id, quantity FROM orders WHERE user_id = ?";
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, user_id);
            ResultSet current = statement.executeQuery();
            String finalJSONString = "{";
            while (current.next()) {
                int prodId = current.getInt("prod_id");
                int quantity = current.getInt("quantity");
                finalJSONString += String.format("\"%d\": %d,", prodId, quantity);
            }

            finalJSONString = finalJSONString.substring(0, finalJSONString.length() - 1);
            finalJSONString += "}";
            return finalJSONString;
        }
        catch (SQLException e) {
            return String.format("{\"error_message\": \"Get Order for user_id %d Did Not Work\"}", user_id);
        }
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
                return 200;
            }
            // As specified in Piazza post @127
            else {
                return 404;
            }
        }
        catch (SQLException e) {
            return 500; // Internal Server Error
        }
    }

    private static String hashPassword(String password) {
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
        // Concatenate the update statement and track if a field is added to the query.
        StringBuilder sqlUpdate = new StringBuilder("UPDATE users SET ");
        boolean isFieldAdded = false;

        // Append the fields to the SQL query if they are provided
        if (username != null && !username.isEmpty()) {
            sqlUpdate.append("username = ?, ");
            isFieldAdded = true;
        }
        if (email != null && !email.isEmpty()) {
            sqlUpdate.append("email = ?, ");
            isFieldAdded = true;
        }
        if (password != null && !password.isEmpty()) {
            sqlUpdate.append("password = ?, ");
            isFieldAdded = true;
        }

        // Return 200 to imply that no fields have been updated but still a success (although no change in the db)
        if (!isFieldAdded) {
            return 200;
        }
        sqlUpdate.setLength(sqlUpdate.length() - 2);
        sqlUpdate.append(" WHERE id = ?");
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sqlUpdate.toString())) {

            // Set parameters for each field
            int valueIndex = 1;
            if (username != null && !username.isEmpty()) {
                statement.setString(valueIndex++, username);
            }
            if (email != null && !email.isEmpty()) {
                statement.setString(valueIndex++, email);
            }
            if (password != null && !password.isEmpty()) {
                statement.setString(valueIndex++, password);
            }
            statement.setInt(valueIndex, id);
            int affectedRows = statement.executeUpdate();
            if (affectedRows > 0) {
                return 200;
            } else {
                return 404;
            }
        }
        catch (SQLException e) {
            return 500; // Internal Server Error
        }
    }
}