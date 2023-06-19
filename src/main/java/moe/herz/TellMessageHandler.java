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
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.pircbotx.hooks.events.MessageEvent;

public class TellMessageHandler {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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

                    // Get current date and time
                    LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Berlin"));

                    // Parse stored time and combine with current date
                    LocalTime storedTime = LocalTime.parse(rs2.getString("timestamp"), DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime storedDateTime = LocalDateTime.of(now.toLocalDate(), storedTime);

                    // If stored time is in future (possible due to late-night messages), subtract 1 day
                    if (storedDateTime.isAfter(now)) {
                        storedDateTime = storedDateTime.minusDays(1);
                    }

                    // Calculate time difference
                    long totalMinutes = ChronoUnit.MINUTES.between(storedDateTime, now);
                    long hours = totalMinutes / 60;
                    long minutes = totalMinutes % 60;

                    // Generate "time ago" string
                    String timeAgo;
                    if (hours > 0) {
                        timeAgo = hours + "h " + minutes + "m ago";
                    } else {
                        timeAgo = minutes + "m ago";
                    }

                    messages.add(sender + " (" + timeAgo + "): " + message);
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
            int sentCount = 0;

            unsentMessages.remove(sender);
            messagesToReceive.put(sender, 0);

            event.getChannel().send().message(sender + ", you have postponed messages: ");
            for (String storedMessage : messages) {
                // Split storedMessage into components
                int firstParenIndex = storedMessage.indexOf('(');
                int lastParenIndex = storedMessage.indexOf(')');
                String timestamp = storedMessage.substring(firstParenIndex + 1, lastParenIndex).trim();
                String messageText = storedMessage.substring(lastParenIndex + 2).trim();

                // Parse timestamp and calculate relative time
                ZonedDateTime messageTime = ZonedDateTime.parse(timestamp, TIME_FORMATTER);
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
                long totalMinutes = ChronoUnit.MINUTES.between(messageTime, now);
                long hours = totalMinutes / 60;
                long minutes = totalMinutes % 60;

                // Format relative time
                String timeAgo;
                if (hours > 0) {
                    timeAgo = hours + "h " + minutes + "m ago";
                } else {
                    timeAgo = minutes + "m ago";
                }

                // Reconstruct storedMessage with relative time
                String formattedMessage = sender + " (" + timeAgo + "): " + messageText;

                // Send storedMessage
                if (sentCount < 3) {
                    event.getChannel().send().message(formattedMessage);
                } else {
                    event.getBot().sendIRC().message(sender, formattedMessage);
                }
                sentCount++;
            }
            if (messages.size() > 3) {
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

