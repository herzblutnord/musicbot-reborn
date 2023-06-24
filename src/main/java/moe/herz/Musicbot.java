package moe.herz;

import java.util.regex.Pattern;
import java.io.IOException;
import javax.net.ssl.SSLSocketFactory;
import java.util.regex.Matcher;
import java.io.FileInputStream;
import java.util.Properties;
import java.sql.Connection;
import java.sql.*;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.User;
import org.pircbotx.hooks.events.InviteEvent;

public class Musicbot extends ListenerAdapter {
    private static final String botName = "UndineWIP";
    private static final String botVersion = "0.5.1";

    private YoutubeService youtubeService;
    private LastFmService lastFmService;
    private TellMessageHandler tellMessageHandler;
    private Properties properties;

    // Database connection
    private Connection db;

    public void loadProperties() {
        try (FileInputStream in = new FileInputStream("./config2.properties")) {
            properties = new Properties();
            properties.load(in);

            // Connect to database
            String databaseURL = properties.getProperty("db.url");
            Connection conn = null;
            try {
                conn = DriverManager.getConnection(databaseURL, properties);
            } catch (SQLException e) {
                e.printStackTrace();
            }

            db = conn;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public Musicbot() {
        loadProperties();
        try {
            youtubeService = new YoutubeService(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
             lastFmService = new LastFmService(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            tellMessageHandler = new TellMessageHandler(db);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        // Configure the bot
        Configuration configuration = new Configuration.Builder()
                .setName(botName)
                .addServer("", 6697)
                .addAutoJoinChannel("#musicbottesting2")
                .addListener(new Musicbot())
                .setSocketFactory(SSLSocketFactory.getDefault()) // Enable SSL
                .buildConfiguration();

        // Start the bot
        try (PircBotX bot = new PircBotX(configuration)) {
            bot.startBot();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Greeting on bot joining
    @Override
    public void onJoin(JoinEvent event) {
        User user = event.getUser();
        if (user != null && user.getNick().equals(botName)) {
            event.getChannel().send().message("Greetings from the depths, I'm " + botName + ", your helpful water spirit! (Version "+ botVersion+ ")");
        }
    }

    // Method to handle general message events
    @Override
    public void onGenericMessage(GenericMessageEvent event) {

        // Regular expression pattern to identify URLs in the message
        Pattern urlPattern = Pattern.compile("(https?://[\\w.-]+\\.[\\w.-]+[\\w./?=&#%\\-\\(\\)]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = urlPattern.matcher(event.getMessage());

        if (event.getMessage().startsWith(".np ")) {
            String username = event.getMessage().substring(4);
            try {
                String response = lastFmService.getCurrentTrack(username);
                event.respondWith(response);
            } catch (Exception e) {
                e.printStackTrace();
                event.respondWith("Error retrieving last.fm data");
            }
        }

        else if (event.getMessage().startsWith(".yt ")) {
            String query = event.getMessage().substring(4);
            String videoUrl = youtubeService.searchYoutube(query);
            if (videoUrl != null) {
                event.respondWith(videoUrl);
            }
        } else {
            // Find urls in the message
            while (matcher.find()) {
                String url = matcher.group(1);

                // Trimming trailing text (if any) from the URL
                int spaceIndex = url.indexOf(' ');
                if (spaceIndex != -1) {
                    url = url.substring(0, spaceIndex);
                }

                if (url.contains("youtube.com/watch?v=")) {
                    String videoId = url.substring(url.indexOf("=") + 1);
                    String videoDetails = youtubeService.getVideoDetails(videoId);
                    if (videoDetails != null) {
                        if (event instanceof MessageEvent messageEvent) {
                            messageEvent.getBot().sendIRC().message(messageEvent.getChannel().getName(), videoDetails);
                        }
                    }
                } else if (url.contains("youtu.be/")) {
                    String videoId = url.substring(url.indexOf("be/") + 3);
                    String videoDetails = youtubeService.getVideoDetails(videoId);
                    if (videoDetails != null) {
                        if (event instanceof MessageEvent messageEvent) {
                            messageEvent.getBot().sendIRC().message(messageEvent.getChannel().getName(), videoDetails);
                        }
                    }
                } else {
                    // Skip non-HTML files
                    String[] skippedExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".mp4", ".mp3", ".wav", ".ogg", ".flac", ".mkv", ".avi", ".flv"};
                    boolean skip = false;
                    for (String extension : skippedExtensions) {
                        if (url.toLowerCase().endsWith(extension)) {
                            skip = true;
                            break;
                        }
                    }

                    if (!skip) {
                        // Use the UrlMetadataFetcher class to get the metadata
                        String metadata = UrlMetadataFetcher.fetchWebsiteMetadata(url);
                        event.respondWith(metadata);
                    }
                }
            }
        }
    }

    @Override
    public void onInvite(InviteEvent event) {
        User user = event.getUser();
        if (user != null && user.getNick() != null && user.getNick().equals("herzblutnord")) {
            String channelName = event.getChannel();
            if (channelName != null) {
                event.getBot().sendIRC().joinChannel(channelName);
            }
        }
    }

    @Override
    public void onMessage(MessageEvent event) {
        User user = event.getUser();
        if (user == null) {
            return;
        }

        String messageText = event.getMessage();
        String sender = user.getNick();

        if (messageText.startsWith(".tell")) {
            tellMessageHandler.handleTellMessage(sender, messageText, event);
        } else {
            tellMessageHandler.handleRegularMessage(sender, event);
        }
    }
}