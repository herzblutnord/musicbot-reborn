package moe.herz;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import javax.net.ssl.SSLSocketFactory;
import java.sql.SQLException;

public class BotInitializer {

    private final Config config;
    private final Musicbot botInstance;

    public BotInitializer() throws SQLException {
        config = new Config();
        YoutubeService youtubeService = new YoutubeService(config);
        LastFmService lastFmService = new LastFmService(config);
        TellMessageHandler tellMessageHandler = new TellMessageHandler(config.getDbConnection());
        UrbanDictionaryService urbanDictionaryService = new UrbanDictionaryService(config);

        botInstance = new Musicbot(youtubeService, lastFmService, tellMessageHandler, urbanDictionaryService, config);
        botInstance.loadIgnoredUrls("ignored_urls.txt");
    }

    public PircBotX initializeBot() {
        Configuration.Builder builder = new Configuration.Builder()
                .setName(botInstance.BOT_NAME)
                .addServer(botInstance.SERVER_NAME, botInstance.SERVER_PORT)
                .addListener(botInstance)
                .setSocketFactory(SSLSocketFactory.getDefault());

        for(String channel : config.getChannelNames()) {
            builder.addAutoJoinChannel(channel.trim());
        }

        Configuration configuration = builder.buildConfiguration();

        // ReminderHandler initialization
        botInstance.reminderHandler.init(); // First, initialize reminders from the database
        botInstance.reminderHandler.cleanupOldReminders(); // Then cleanup old reminders
        botInstance.reminderHandler.init(); // Finally, reinitialize reminders from the updated database

        return new PircBotX(configuration);
    }

    public ReminderHandler getReminderHandler() {
        return botInstance.reminderHandler;
    }

}
