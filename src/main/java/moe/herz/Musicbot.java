package moe.herz;

import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.net.ssl.SSLSocketFactory;
import java.util.List;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.User;
import org.pircbotx.hooks.events.InviteEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

public class Musicbot extends ListenerAdapter {
    private final YoutubeService youtubeService;
    private final LastFmService lastFmService;
    private final TellMessageHandler tellMessageHandler;
    private final UrbanDictionaryService urbanDictionaryService;
    private final HelpService helpService;

    private final String BOT_NAME;
    private final String BOT_VERSION = "0.7.1";
    private final String SERVER_NAME;
    private final int SERVER_PORT;
    private final String CHANNEL_NAME;
    private final ReminderHandler reminderHandler;

    public Musicbot(YoutubeService youtubeService, LastFmService lastFmService, TellMessageHandler tellMessageHandler, UrbanDictionaryService urbanDictionaryService, Config config) {
        this.youtubeService = youtubeService;
        this.lastFmService = lastFmService;
        this.tellMessageHandler = tellMessageHandler;
        this.BOT_NAME = config.getProperty("bot.name");
        this.SERVER_NAME = config.getProperty("server.name");
        this.SERVER_PORT = Integer.parseInt(config.getProperty("server.port"));
        this.CHANNEL_NAME = config.getProperty("channel.name");
        this.reminderHandler = new ReminderHandler(config.getDbConnection());
            reminderHandler.init(); // First, initialize reminders from the database
            reminderHandler.cleanupOldReminders(); // Then cleanup old reminders
            reminderHandler.init(); // Finally, reinitialize reminders from the updated database
        this.urbanDictionaryService = urbanDictionaryService;
        this.helpService = new HelpService();
    }

    public static void main(String[] args) throws SQLException {
        Config config = new Config();
        YoutubeService youtubeService = new YoutubeService(config);
        LastFmService lastFmService = new LastFmService(config);
        TellMessageHandler tellMessageHandler = new TellMessageHandler(config.getDbConnection());
        UrbanDictionaryService urbanDictionaryService = new UrbanDictionaryService(config);

        Musicbot botInstance = new Musicbot(youtubeService, lastFmService, tellMessageHandler, urbanDictionaryService, config);

        Configuration configuration = new Configuration.Builder()
                .setName(botInstance.BOT_NAME)
                .addServer(botInstance.SERVER_NAME, botInstance.SERVER_PORT)
                .addAutoJoinChannel(botInstance.CHANNEL_NAME)
                .addListener(botInstance)
                .setSocketFactory(SSLSocketFactory.getDefault())
                .buildConfiguration();

        try (PircBotX bot = new PircBotX(configuration)) {
            // Initialize reminderHandler and start the reminder sender thread
            botInstance.reminderHandler.init();
            Thread reminderSenderThread = new Thread(new ReminderSender(botInstance.reminderHandler, bot));

            reminderSenderThread.start();

            bot.startBot();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onJoin(JoinEvent event) {
        User user = event.getUser();
        if (user != null && user.getNick().equals(BOT_NAME)) {
            event.getChannel().send().message("Greetings from the depths, I'm " + BOT_NAME + ", your helpful water spirit! (Version " + BOT_VERSION + ")");
        }
    }

    @Override
    public void onGenericMessage(GenericMessageEvent event) {
        String message = event.getMessage();
        Pattern urlPattern = Pattern.compile("(https?://[\\w.-]+\\.[\\w.-]+[\\w./?=&#%\\-\\(\\)]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = urlPattern.matcher(message);


        if (message.startsWith(".help")) {
            handleHelpCommand(event);
        } else if (message.startsWith(".np")) {
            handleNowPlayingCommand(event, message);
        } else if (message.startsWith(".in ")) {
            handleReminderCommand(event, message);
        } else if (message.startsWith(".yt ")) {
            handleYoutubeCommand(event, message);
        } else if (message.startsWith(".ud ")) {
            handleUrbanDictionaryCommand(event, message);
        } else {
            handleUrlFetching(event, matcher);
        }
    }

    private void handleNowPlayingCommand(GenericMessageEvent event, String message) {
        String ircUsername = event.getUser().getNick();
        String username = null;

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
            e.printStackTrace();
        }
    }



    private void handleYoutubeCommand(GenericMessageEvent event, String message) {
        String query = message.substring(4);
        String videoUrl = youtubeService.searchYoutube(query);
        if (videoUrl != null) {
            event.respondWith(videoUrl);
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

        while (matcher.find()) {
            String url = matcher.group(1);
            int spaceIndex = url.indexOf(' ');
            if (spaceIndex != -1) {
                url = url.substring(0, spaceIndex);
            }

            // For youtube.com/watch?v= and youtu.be/ links, get and send video details
            if (url.contains("youtube.com/watch?v=") || url.contains("youtu.be/")) {
                String videoId = url.contains("youtube.com/watch?v=") ? url.substring(url.indexOf("=") + 1) : url.substring(url.indexOf("be/") + 3);
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
