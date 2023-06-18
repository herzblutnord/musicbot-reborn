package moe.herz;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.time.ZonedDateTime;

import org.pircbotx.hooks.events.MessageEvent;

public class TellMessageHandler {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));
    private static final int MAX_UNSENT_MESSAGES = 5;
    private static final int MAX_RECEIVED_MESSAGES = 10;

    private final Map<String, LinkedList<String>> unsentMessages = new HashMap<>();
    private final Map<String, Integer> messagesToReceive = new HashMap<>();

    private final Connection db;

    public TellMessageHandler(Connection db) {
        this.db = db;

        try {
            Statement st = this.db.createStatement();

            ResultSet rs = st.executeQuery("SELECT * FROM tellNew"); // Using a different table name
            while (rs.next()) {
                String recipient = rs.getString("recipient");
                messagesToReceive.put(recipient, messagesToReceive.getOrDefault(recipient, 0) + 1);

                Statement st2 = this.db.createStatement();
                ResultSet rs2 = st2.executeQuery("SELECT * FROM tellNew WHERE recipient = '" + recipient + "'");
                LinkedList<String> messages = new LinkedList<>();
                while (rs2.next()) {
                    String sender = rs2.getString("sender");
                    String message = rs2.getString("message");
                    String timestamp = rs2.getString("timestamp");
                    messages.add(sender + " (" + timestamp + "): " + message);
                }
                unsentMessages.put(recipient, messages);
                st2.close();
                rs2.close();
            }
            st.close();
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void handleTellMessage(String sender, String messageText, MessageEvent event) {
        String[] parts = messageText.split(" ", 3);
        if (parts.length != 3) {
            event.respond("Invalid .tell command. Usage: .tell <nick> <message>");
        } else {
            String recipient = parts[1];
            String message = parts[2];
            String timestamp = ZonedDateTime.now().format(TIME_FORMATTER);

            if (recipient.equalsIgnoreCase(sender)) {
                event.respond("Aww, talking to yourself? How pitiful...");
                return;
            } else if (recipient.equalsIgnoreCase(event.getBot().getNick())) {
                event.respond("I am right here, baka!");
                return;
            }

            if (messagesToReceive.getOrDefault(recipient, 0) >= MAX_RECEIVED_MESSAGES) {
                event.respond("This user has too many messages to receive.");
                return;
            }

            LinkedList<String> recipientMessages = unsentMessages.getOrDefault(recipient, new LinkedList<>());
            if (recipientMessages.stream().filter(m -> m.startsWith(sender + " (")).count() >= MAX_UNSENT_MESSAGES) {
                event.respond("You have too many pending messages for this user.");
                return;
            }

            unsentMessages.computeIfAbsent(recipient, k -> new LinkedList<>()).add(sender + " (" + timestamp + "): " + message);
            messagesToReceive.put(recipient, messagesToReceive.getOrDefault(recipient, 0) + 1);

            try {
                Statement st = db.createStatement();
                st.executeUpdate("INSERT INTO tellNew (sender, recipient, message, timestamp) VALUES ('" + sender + "', '" + recipient + "', '" + message + "', '" + timestamp + "')");
                st.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            event.getChannel().send().message("Your message will be delivered the next time " + recipient + " is here!");
        }
    }

    public void handleRegularMessage(String sender, MessageEvent event) {
        if (unsentMessages.containsKey(sender)) {
            LinkedList<String> messages = unsentMessages.get(sender);
            int messageCount = messages.size();
            int sentCount = 0;

            unsentMessages.remove(sender);
            messagesToReceive.put(sender, 0);

            event.getChannel().send().message(sender + ", you have postponed messages: ");
            for (String message : messages) {
                if(sentCount < 3){
                    event.getChannel().send().message(message);
                } else {
                    event.getBot().sendIRC().message(sender, message);
                }
                sentCount++;
            }
            if (messageCount > 3) {
                event.getChannel().send().message("The remaining messages were sent via DM");
            }

            try {
                Statement st = db.createStatement();
                st.executeUpdate("DELETE FROM tellNew WHERE recipient = '" + sender + "'");
                st.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

