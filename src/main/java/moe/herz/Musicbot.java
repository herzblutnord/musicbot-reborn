package moe.herz;

import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.net.ssl.SSLSocketFactory;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.User;
import org.pircbotx.hooks.events.InviteEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.ConnectEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Musicbot extends ListenerAdapter {
    private final YoutubeService youtubeService;
    private final LastFmService lastFmService;
    private final TellMessageHandler tellMessageHandler;
    private final UrbanDictionaryService urbanDictionaryService;
    private final HelpService helpService;
    private Set<String> ignoredUrls;
    private final String BOT_NAME;
    private final String BOT_VERSION = "0.8.2 rev. 1";
    private final String BOT_NICKSERV_PW;
    private final String BOT_NICKSERV_EMAIL;
    private final String BOT_ADMIN;
    private final String SERVER_NAME;
    private final int SERVER_PORT;
    public String[] CHANNEL_NAMES;
    private final ReminderHandler reminderHandler;
    private final Config config;
    private static final Logger logger = LoggerFactory.getLogger(Musicbot.class);

    public Musicbot(YoutubeService youtubeService, LastFmService lastFmService, TellMessageHandler tellMessageHandler, UrbanDictionaryService urbanDictionaryService, Config config) {
        this.config = config;
        this.youtubeService = youtubeService;
        this.lastFmService = lastFmService;
        this.tellMessageHandler = tellMessageHandler;
        this.BOT_NAME = config.getProperty("bot.name");
        this.SERVER_NAME = config.getProperty("server.name");
        this.SERVER_PORT = Integer.parseInt(config.getProperty("server.port"));
        this.CHANNEL_NAMES = config.CHANNEL_NAMES;  // Populate from Config
        this.reminderHandler = new ReminderHandler(config.getDbConnection());
        reminderHandler.init(); // First, initialize reminders from the database
        reminderHandler.cleanupOldReminders(); // Then cleanup old reminders
        reminderHandler.init(); // Finally, reinitialize reminders from the updated database
        this.urbanDictionaryService = urbanDictionaryService;
        this.helpService = new HelpService();
        this.BOT_NICKSERV_PW = config.getProperty("nickserv.pw");
        this.BOT_NICKSERV_EMAIL = config.getProperty("nickserv.email");
        this.BOT_ADMIN = config.getProperty("bot.admin");
    }

    public static void main(String[] args) throws SQLException {
        Config config = new Config();
        YoutubeService youtubeService = new YoutubeService(config);
        LastFmService lastFmService = new LastFmService(config);
        TellMessageHandler tellMessageHandler = new TellMessageHandler(config.getDbConnection());
        UrbanDictionaryService urbanDictionaryService = new UrbanDictionaryService(config);

        Musicbot botInstance = new Musicbot(youtubeService, lastFmService, tellMessageHandler, urbanDictionaryService, config);
        botInstance.loadIgnoredUrls("ignored_urls.txt");

        Configuration.Builder builder = new Configuration.Builder()
                .setName(botInstance.BOT_NAME)
                .addServer(botInstance.SERVER_NAME, botInstance.SERVER_PORT)
                .addListener(botInstance)
                .setSocketFactory(SSLSocketFactory.getDefault());

        for(String channel : config.CHANNEL_NAMES) {
            builder.addAutoJoinChannel(channel.trim());
        }

        Configuration configuration = builder.buildConfiguration();

        try (PircBotX bot = new PircBotX(configuration)) {
            // Initialize reminderHandler and start the reminder sender thread
            botInstance.reminderHandler.init();
            Thread reminderSenderThread = new Thread(new ReminderSender(botInstance.reminderHandler, bot));

            reminderSenderThread.start();

            bot.startBot();
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
    }

    @Override
    public void onConnect(ConnectEvent event) {
        boolean isRegistered = config.isBotRegistered(SERVER_NAME);

        if (isRegistered) {
            // Send identify command to NickServ
            event.getBot().sendIRC().message("NickServ", "IDENTIFY " + BOT_NICKSERV_PW);
        } else {
            // Register with NickServ and save to the database
            event.getBot().sendIRC().message("NickServ", "REGISTER " + BOT_NICKSERV_PW + " " + BOT_NICKSERV_EMAIL);
            config.setBotRegistered(SERVER_NAME);
        }
    }


    @Override
    public void onGenericMessage(GenericMessageEvent event) {
        // Ignore private/direct messages
        if (event instanceof PrivateMessageEvent) {
            return;
        }

        String message = event.getMessage();
        User user = event.getUser();
        String nick = user != null ? user.getNick() : "";
        Pattern urlPattern = Pattern.compile("(https?://[\\w.-]+\\.[\\w.-]+[\\w./?=&#%\\-()@]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = urlPattern.matcher(message);

        if (message.startsWith(".help")) {
            handleHelpCommand(event);
        } else if (message.startsWith("!botcheck")){
            event.respondWith("Greetings from the depths, I'm " + BOT_NAME + ", your helpful water spirit! (Version " + BOT_VERSION + ")");
        } else if (message.startsWith(".np")) {
            handleNowPlayingCommand(event, message);
        } else if (message.startsWith(".in ")) {
            handleReminderCommand(event, message);
        } else if (message.startsWith(".yt ")) {
            handleYoutubeCommand(event, message);
        } else if (message.startsWith(".ud ")) {
            handleUrbanDictionaryCommand(event, message);
        } else if (message.startsWith("!reload")) {
            if (nick != null && nick.equals(BOT_ADMIN)) {
                loadIgnoredUrls("ignored_urls.txt");
                event.respondWith("Ignore list reloaded.");
            } else {
                event.respondWith("You're not my master! Hmpf!");
            }
        } else {
            handleUrlFetching(event, matcher);
        }
    }


    private void handleNowPlayingCommand(GenericMessageEvent event, String message) {
        String ircUsername = event.getUser().getNick();
        String username;

        if (message.length() > 4) {
            // Extract the username from the message if it's provided
            username = message.substring(4);
            lastFmService.saveLastFmUsername(ircUsername, username);
        } else {
            // If no Last.fm username was specified in the message, get it from the database
            username = lastFmService.getLastFmUsernameFromDb(ircUsername);

            // If the Last.fm username couldn't be retrieved from the database, there's nothing more to do
            if (username == null) {
                event.respondWith("No Last.fm username associated with " + ircUsername + ". Please provide your Last.fm username.");
                return;
            }
        }

        try {
            String response = lastFmService.getCurrentTrack(username);
            event.respondWith(response);
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
    }

    private void handleYoutubeCommand(GenericMessageEvent event, String message) {
        String query = message.substring(4);
        String videoUrl = youtubeService.searchYoutube(query);
        if (videoUrl != null) {
            event.respondWith(videoUrl);
        }
    }

    private void loadIgnoredUrls(String filePath) {
        try {
            ignoredUrls = new HashSet<>(Files.readAllLines(Paths.get(filePath)));
        } catch (IOException e) {
            logger.error("An error occurred", e);
        }
    }

    private void handleUrbanDictionaryCommand(GenericMessageEvent event, String message) {
        String term = message.substring(4);
        List<String> definitions = urbanDictionaryService.searchUrbanDictionary(term);
        for (int i = 0; i < definitions.size() && i < 4; i++) {
            String definition = definitions.get(i);
            if (!definition.trim().isEmpty()) {
                event.respondWith(definition);
            }
        }
        if (definitions.size() > 4) {
            event.respondWith("... [message truncated due to length]");
        }
    }

    private void handleUrlFetching(GenericMessageEvent event, Matcher matcher) {
        if (matcher.find()) {
            String url = matcher.group(1);

            boolean shouldIgnore = false;
            for (String ignoredUrl : ignoredUrls) {
                if (url.startsWith(ignoredUrl)) {
                    shouldIgnore = true;
                    break;
                }
            }
            if (shouldIgnore) {
                return;  // Exit the method if the URL should be ignored
            }

            String videoId = null;

            if (url.contains("youtube.com/watch?v=")) {
                Pattern pattern = Pattern.compile("v=([^&]*)");
                Matcher videoMatcher = pattern.matcher(url);
                if (videoMatcher.find()) {
                    videoId = videoMatcher.group(1);
                }
            } else if (url.contains("youtu.be/")) {
                Pattern pattern = Pattern.compile("youtu\\.be/([^?&]*)");
                Matcher videoMatcher = pattern.matcher(url);
                if (videoMatcher.find()) {
                    videoId = videoMatcher.group(1);
                }
            } else if (url.contains("youtube.com/playlist?list=")) {
                Pattern pattern = Pattern.compile("list=([^&]*)");
                Matcher playlistMatcher = pattern.matcher(url);
                if (playlistMatcher.find()) {
                    String playlistId = playlistMatcher.group(1);
                    String playlistDetails = youtubeService.getPlaylistDetails(playlistId);
                    if (playlistDetails != null) {
                        event.respondWith(playlistDetails);
                    }
                }
            } else if (url.contains("youtube.com/@")) {
                Pattern pattern = Pattern.compile("@([a-zA-Z0-9_-]+)");
                Matcher usernameMatcher = pattern.matcher(url);
                if (usernameMatcher.find()) {
                    String username = usernameMatcher.group(1);
                    // Use your new method to get the channel ID from the username
                    String channelId = youtubeService.getChannelIdFromUsernameUsingSearch(username);
                    if (channelId != null) {
                        String channelDetails = youtubeService.getChannelDetails(channelId);
                        if (channelDetails != null) {
                            event.respondWith(channelDetails);
                        }
                    }
                }

        } else if (url.contains("youtube.com/channel/")) {
                Pattern pattern = Pattern.compile("channel/([a-zA-Z0-9_-]+)");
                Matcher channelMatcher = pattern.matcher(url);
                if (channelMatcher.find()) {
                    String channelId = channelMatcher.group(1);
                    String channelDetails = youtubeService.getChannelDetails(channelId);
                    if (channelDetails != null) {
                        event.respondWith(channelDetails);
                    }
                }
            }

            if (videoId != null) {
                String videoDetails = youtubeService.getVideoDetails(videoId);
                if (videoDetails != null) {
                    if (event instanceof MessageEvent messageEvent) {
                        messageEvent.getBot().sendIRC().message(messageEvent.getChannel().getName(), videoDetails);
                    }
                }
            } else {
                // Skip non-HTML files
                String[] skippedExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".webm", ".mp4", ".mp3", ".wav", ".ogg", ".flac", ".mkv", ".avi", ".flv"};
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

    @Override
    public void onInvite(InviteEvent event) {
        User user = event.getUser();
        if (user != null && user.getNick() != null && user.getNick().equals(BOT_ADMIN)) {
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
        String currentChannel = event.getChannel().getName();
        String currentServer = SERVER_NAME;

        if (messageText.startsWith(".tell")) {
            tellMessageHandler.handleTellMessage(sender, messageText, event, currentServer, currentChannel);
        } else {
            tellMessageHandler.handleRegularMessage(sender, event, currentServer, currentChannel);
        }
    }

    private void handleReminderCommand(GenericMessageEvent event, String message) {
        String sender = event.getUser().getNick();
        if (event instanceof MessageEvent messageEvent) {
            reminderHandler.processReminderRequest(sender, message, messageEvent.getChannel().getName(), event);
        } else if (event instanceof PrivateMessageEvent) {
            // Handle the case for a private message
            reminderHandler.processReminderRequest(sender, message, sender, event);
        }
    }

    private void handleHelpCommand(GenericMessageEvent event) {
        User user = event.getUser();
        if(user == null) {
            return;
        }

        if (event instanceof MessageEvent messageEvent) {
            messageEvent.getChannel().send().message("I will send you a list of all my commands per DM");
            helpService.sendHelp(user, event.getBot());
        } else if (event instanceof PrivateMessageEvent) {
            helpService.sendHelp(user, event.getBot());
        }
    }

}
