package moe.herz;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
    private final Properties properties;
    private Connection db;
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    public Config() {
        properties = new Properties();
        try (FileInputStream in = new FileInputStream("./config2.properties")) {
            properties.load(in);
        } catch (IOException e) {
            logger.error("An error occurred", e);
        }
        setupDatabaseConnection();
    }

    private void setupDatabaseConnection() {
        String databaseURL = properties.getProperty("db.url");
        try {
            db = DriverManager.getConnection(databaseURL, properties);
        } catch (SQLException e) {
            logger.error("An error occurred", e);
        }
    }

    public String getBotName() {
        return properties.getProperty("bot.name", "DefaultBotName");
    }

    public String getServerName() {
        return properties.getProperty("server.name", "DefaultServer");
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "6667"));
    }

    public String[] getChannelNames() {
        return properties.getProperty("channel.name", "").split(",");
    }

    public String getNickservPw() {
        return properties.getProperty("nickserv.pw");
    }

    public String getNickservEmail() {
        return properties.getProperty("nickserv.email");
    }

    public String getytapiKey() {
        return properties.getProperty("yt.apiKey");
    }

    public String getlastfmapiKey() {
        return properties.getProperty("lfm.apiKey");
    }

    public String getudapiKey() {
        return properties.getProperty("ud.apiKey");
    }

    public String getBotAdmin() {
        return properties.getProperty("bot.admin");
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
            logger.error("An error occurred", e);
        }
        return false;
    }

    public void setBotRegistered(String serverName) {
        try (PreparedStatement stmt = db.prepareStatement("INSERT INTO nickserv_registration(server_name, is_registered) VALUES(?, true) ON CONFLICT(server_name) DO UPDATE SET is_registered = EXCLUDED.is_registered")) {
            stmt.setString(1, serverName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("An error occurred", e);
        }
    }
}
