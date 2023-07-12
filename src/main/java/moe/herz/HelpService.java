package moe.herz;

import org.pircbotx.User;
import org.pircbotx.PircBotX;

import java.util.HashMap;
import java.util.Map;

public class HelpService {
    private final Map<String, String> commands = new HashMap<>();

    public HelpService() {
        commands.put(
                ".yt <search term>",
                "Searches YouTube and returns a video matching the provided search term."
        );
        commands.put(
                ".np <last.fm username>",
                "Displays the most recent song played by the specified Last.fm username."
        );
        commands.put(
                ".in <duration (w/d/h/m/s)> <message>",
                "Sets a reminder for you. You'll be notified with the provided message after the specified duration. Duration format: Number followed by 'w' for weeks, 'd' for days, 'h' for hours, 'm' for minutes, or 's' for seconds (e.g., '10m' for 10 minutes)."
        );
        commands.put(
                ".ud <search term>",
                "Searches Urban Dictionary and provides a definition for the specified term."
        );
        commands.put(
                ".tell <username> <message>",
                "Saves a message for a user. The user will receive the message the next time they are active."
        );
    }

    public void sendHelp(User user, PircBotX bot) {
        bot.sendIRC().message(user.getNick(), "Here are all my commands:");

        for (Map.Entry<String, String> command : commands.entrySet()) {
            bot.sendIRC().message(user.getNick(), command.getKey() + " - " + command.getValue());
        }
    }
}
