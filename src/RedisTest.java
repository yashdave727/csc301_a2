import redis.clients.jedis.Jedis;

public class RedisTest {
    public static void main(String[] args) {
        // Connect to your Redis server.
        try (Jedis jedis = new Jedis("localhost", 6379)) { // Replace "localhost" with your Redis server IP if needed.
            // Check the connection
            String response = jedis.ping();
            if ("PONG".equals(response)) {
                System.out.println("Connected to Redis successfully!");
            } else {
                System.out.println("Failed to connect to Redis.");
            }
        } catch (Exception e) {
            System.out.println("Error while connecting to Redis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
