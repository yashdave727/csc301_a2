import java.sql.*;

/**
 * UserDatabase class provides methods for managing user data in a SQLite database.
 */
public class ProductDatabase {
    private final String url = "jdbc:postgresql://localhost:5435/assignmentdb";
    private final String user = "assignmentuser";
    private final String password = "assignmentpassword";

    /**
     * Constructor for the UserDatabase. This method initializes the database using the initialize().
     */
    public ProductDatabase() {
        initialize();
    }

    /**
     * The connect method is used to establish a connection to the SQLite database.
     *
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
    private void initialize() {
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
        String sql = "SELECT id, name, description, price, quantity FROM products WHERE id = ?"; // Make sure the table name is 'products' not 'users'
        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            ResultSet current = statement.executeQuery();
            if (current.next()) {
                int productId = current.getInt("id");
                String name = current.getString("name");
                String description = current.getString("description");
                float price = current.getFloat("price"); // Use getFloat for price
                int quantity = current.getInt("quantity"); // Use getInt for quantity
                return String.format("{\"id\": %d, \"name\": \"%s\"," +
                                " \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}\n", productId, name, description,
                        price, quantity);
            }
        }
        catch (SQLException e) {
            return "";
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
            statement.executeUpdate();
            return 200;
        }
        catch (SQLException e) {
            if (e.getSQLState().equals("23505")) {
                return 409; // Duplicate entry
            }
            else {
                return 500; // Internal Server Error
            }
        }
    }

    public int deleteProduct(int id, String name, String description, float price, int quantity) {
        String sql = "DELETE FROM products WHERE id = ? AND name = ? AND description = ? AND price = ? AND" +
                " quantity = ?";

        try (Connection con = this.connect();
             PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setFloat(4, price);
            statement.setInt(5, quantity);
            int affectedRows = statement.executeUpdate();

            // Product had been deleted if number of rows has changed
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

    public int updateProduct(int id, String name, String description, float price, int quantity) {
        StringBuilder sqlUpdate = new StringBuilder("UPDATE products SET ");
        int valueCount = 0;

        // Check if the price or quantity is provided and less than or equal to 0, return 400 for bad request.
        if ((price != 0 && price <= 0) || (quantity != 0 && quantity <= 0)) {
            return 400;
        }

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

        // Return 200 to imply that no fields have been updated but still a success (although no change in the db)
        if (valueCount == 0) {
            return 200;
        }
        sqlUpdate.delete(sqlUpdate.length() - 2, sqlUpdate.length());
        sqlUpdate.append(" WHERE id = ?");
        try (Connection conn = this.connect();
             PreparedStatement statement = conn.prepareStatement(sqlUpdate.toString())) {
            int valueIndex = 1;
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
                return 200;
            }
            else {
                return 404; // Not Found - Product with given ID not found
            }
        }
        catch (SQLException e) {
            return 500; // Internal Server Error
        }
    }


}
