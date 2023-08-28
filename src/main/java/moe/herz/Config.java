package moe.herz;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Config {
    private final Properties properties;
    private Connection db;

    public Config() {
        properties = new Properties();
        try (FileInputStream in = new FileInputStream("./config2.properties")) {
            properties.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        setupDatabaseConnection();
    }

    private void setupDatabaseConnection() {
        String databaseURL = properties.getProperty("db.url");
        try {
            db = DriverManager.getConnection(databaseURL, properties);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public Connection getDbConnection() {
        return db;
    }

    public boolean isBotRegistered(String serverName) {
        try (PreparedStatement stmt = db.prepareStatement("SELECT is_registered FROM nickserv_registration WHERE server_name = ?")) {
            stmt.setString(1, serverName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_registered");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setBotRegistered(String serverName) {
        try (PreparedStatement stmt = db.prepareStatement("INSERT INTO nickserv_registration(server_name, is_registered) VALUES(?, true) ON CONFLICT(server_name) DO UPDATE SET is_registered = EXCLUDED.is_registered")) {
            stmt.setString(1, serverName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
