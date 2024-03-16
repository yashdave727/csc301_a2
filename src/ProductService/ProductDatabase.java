import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;

/**
 * UserDatabase class provides methods for managing user data in a database.
 */
public class ProductDatabase {

    private static HikariDataSource dataSource;

    static {
        // Configure HikariCP
        HikariConfig config = new HikariConfig();
        // Adjust the JDBC URL, username, and password to match your PostgreSQL container setup
        config.setJdbcUrl("jdbc:postgresql://142.1.44.57:5432/assignmentdb");
        config.setUsername("assignmentuser");
        config.setPassword("assignmentpassword");

        // // Optional: Configure additional HikariCP settings as needed
        // config.addDataSourceProperty("cachePrepStmts", "true");
        // config.addDataSourceProperty("prepStmtCacheSize", "250");
        // config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
    }

    /**
     * The connect method is used to establish a connection to the database.
     * @return value is a connection object to the database.
     */
    private Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * The initialize method which is used in the constructor is for initializing the database by creating a table
     * for users if it does not already exist.
     * @param dockerIp is the IP address of the Docker container running the database.
     * @param dbPort is the port number of the database.
     * @param redisPort is the port number of the Redis server.
     */
    public void initialize(String dockerIp, String dbPort, String redisPort) {
	// url = "jdbc:postgresql://" + dockerIp + ":" + dbPort + "/assignmentdb";
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

    public static void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("User Database connection pool successfully shut down.");
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
                return 400; // Internal Server Error
            }
        }
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
            return 404; // Internal Server Error
        }
    }


}
