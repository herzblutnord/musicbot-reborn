package moe.herz;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
}
