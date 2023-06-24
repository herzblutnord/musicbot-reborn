package moe.herz;

import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.net.ssl.SSLSocketFactory;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.User;
import org.pircbotx.hooks.events.InviteEvent;

public class Musicbot extends ListenerAdapter {
    private static final String BOT_NAME = "UndineWIP";
    private static final String BOT_VERSION = "0.5.2";

    private final YoutubeService youtubeService;
    private final LastFmService lastFmService;
    private final TellMessageHandler tellMessageHandler;

    public Musicbot(YoutubeService youtubeService, LastFmService lastFmService, TellMessageHandler tellMessageHandler) {
        this.youtubeService = youtubeService;
        this.lastFmService = lastFmService;
        this.tellMessageHandler = tellMessageHandler;
    }

    public static void main(String[] args) throws SQLException {
        Config config = new Config();
        YoutubeService youtubeService = new YoutubeService(config);
        LastFmService lastFmService = new LastFmService(config);
        TellMessageHandler tellMessageHandler = new TellMessageHandler(config.getDbConnection());

        Musicbot botInstance = new Musicbot(youtubeService, lastFmService, tellMessageHandler);

        Configuration configuration = new Configuration.Builder()
                .setName(BOT_NAME)
                .addServer("", 6697)
                .addAutoJoinChannel("#musicbottesting2")
                .addListener(botInstance)
                .setSocketFactory(SSLSocketFactory.getDefault())
                .buildConfiguration();

        try (PircBotX bot = new PircBotX(configuration)) {
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

        if (message.startsWith(".np ")) {
            handleNowPlayingCommand(event, message);
        } else if (message.startsWith(".yt ")) {
            handleYoutubeCommand(event, message);
        } else {
            handleUrlFetching(event, matcher);
        }
    }

    private void handleNowPlayingCommand(GenericMessageEvent event, String message) {
        String username = message.substring(4);
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
}
