import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

/**
 * UserDatabase class provides methods for managing user data in a SQLite database.
 */
class UserDatabase {

    private final String url = "jdbc:postgresql://localhost:5435/assignmentdb";
    private final String user = "assignmentuser";
    private final String password = "assignmentpassword";


    /**
     * Constructor for the UserDatabase. This method initializes the database using the initialize().
     */
    public UserDatabase() {
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
            String sql = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY," +
                    "username TEXT NOT NULL," +
                    "email TEXT NOT NULL," +
                    "password TEXT NOT NULL)";
            statement.execute(sql);
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
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
            statement.setString(4, password);
            statement.executeUpdate();
            return 200; // OK - User created successfully
        }
        catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return 409;
            }
            else {
                return 500; // Internal Server Error
            }
        }
    }

    /**
     * Retrieves a user's information from the database based on the user ID.
     * @param id is the ID of the user to retrieve.
     * @return A JSON string containing the user's information, or an empty string if not found.
     */
    public String getUser(int id) {
        String sql = "SELECT id, username, email, password FROM users WHERE id = ?";
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            ResultSet current = statement.executeQuery();
            if (current.next()) {
                int userId = current.getInt("id");
                String username = current.getString("username");
                String email = current.getString("email");
                String encryptedPassword = hashPassword(current.getString("password"));
                return String.format("{\"id\": %d, \"username\": \"%s\"," +
                        " \"email\": \"%s\", \"password\": \"%s\"}", userId, username, email, encryptedPassword);
            }
        }
        catch (SQLException e) {
            return "";
        }
        return "";
    }

    public int deleteUser(int id, String username, String email, String password) {
        String sql = "DELETE FROM users WHERE id = ? AND username = ? AND email = ? AND password = ?";

        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, username);
            statement.setString(3, email);
            statement.setString(4, password);
            int affectedRows = statement.executeUpdate();

            // User had been deleted if number of rows has changed
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
