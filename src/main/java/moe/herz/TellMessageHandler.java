package moe.herz;

import org.ocpsoft.prettytime.PrettyTime;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.pircbotx.hooks.events.MessageEvent;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TellMessageHandler {
    private static final int MAX_MESSAGES_PER_USER = 10;
    private static final int MAX_MESSAGES_TO_SINGLE_USER = 5;
    private static final int MAX_MESSAGES_IN_CHANNEL = 3;
    private static final Logger logger = LoggerFactory.getLogger(TellMessageHandler.class);


    private final Connection db;
    private final LinkedList<Message> messageList;
    private final PrettyTime pTime;

    public TellMessageHandler(Connection db) throws SQLException {
        this.db = db;
        this.messageList = new LinkedList<>();
        this.pTime = new PrettyTime(Locale.ENGLISH);  // Set the locale to English
        loadMessagesFromDatabase();
    }

    private static class Message {
        String sender;
        String recipient;
        String message;
        Timestamp timestamp;
        String server;
        String channel;

        Message(String sender, String recipient, String message, Timestamp timestamp, String server, String channel) {
            this.sender = sender;
            this.recipient = recipient;
            this.message = message;
            this.timestamp = timestamp;
            this.server = server;
            this.channel = channel;
        }
    }

    private String sanitizeMessage(String originalMessage) {
        return originalMessage
                .replaceAll("\\p{C}", "")  // Removes control characters
                .replaceAll("\\p{Z}", "")  // Removes separator characters including zero-width space
                .trim();  // Removes leading and trailing whitespace
    }

    private void loadMessagesFromDatabase() throws SQLException {
        String sql = "SELECT sender, recipient, message, timestamp, server, channel FROM tellnew";
        PreparedStatement statement = db.prepareStatement(sql);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            Message message = new Message(
                    rs.getString("sender"),
                    rs.getString("recipient"),
                    rs.getString("message"),
                    rs.getTimestamp("timestamp"),
                    rs.getString("server"),
                    rs.getString("channel")
            );
            messageList.add(message);
        }
    }

    public void handleTellMessage(String sender, String messageText, GenericMessageEvent event, String server, String channel) {
        String[] parts = messageText.split(" ", 3);
        if (parts.length < 3) {
            event.respond("Invalid .tell command. Usage: .tell <nick> <message>");
            return;
        }

        String recipient = parts[1];
        String message = parts[2];

        // Sanitize the message
        String sanitizedMessage = sanitizeMessage(message);

        // Check if the sanitized message is empty
        if (sanitizedMessage.isEmpty()) {
            event.respond("Why are you trying to send an empty message? Baka!");
            return;
        }

        if (recipient.equalsIgnoreCase(sender)) {
            event.respond("Aww, talking to yourself? How pitiful...");
        } else if (recipient.equalsIgnoreCase(event.getBot().getNick())) {
            event.respond("I am right here, baka!");
        } else {
            try {
                // Checks and responses for exceeded limits
                if (getTotalMessagesForUser(recipient) >= MAX_MESSAGES_PER_USER) {
                    event.respond("This user has too many messages to receive.");
                    return;
                } else if (getTotalMessagesFromUserToUser(sender, recipient) >= MAX_MESSAGES_TO_SINGLE_USER) {
                    event.respond("You have too many pending messages for this user.");
                    return;
                }

                // Save to DB and memory
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                saveMessageToDatabase(sender, recipient, sanitizedMessage, timestamp, server, channel);  // Note the use of sanitizedMessage
                messageList.add(new Message(sender, recipient, sanitizedMessage, timestamp, server, channel));  // Note the use of sanitizedMessage
                if (event instanceof MessageEvent messageEvent) {
                    event.getBot().sendIRC().message(messageEvent.getChannel().getName(), "Your message will be delivered the next time " + recipient + " is here!");
                } else {
                    event.respond("Your message will be delivered the next time " + recipient + " is here!");
                }
        } catch (SQLException e) {
                logger.error("An error occurred", e);
            }
        }
    }

    public void handleRegularMessage(String sender, GenericMessageEvent event, String currentServer, String currentChannel) {
        Iterator<Message> iter = messageList.iterator();
        List<Message> userMessages = new ArrayList<>();
        while (iter.hasNext()) {
            Message message = iter.next();
            if (message.recipient.equalsIgnoreCase(sender)
                    && message.server.equals(currentServer)
                    && message.channel.equals(currentChannel)) {
                userMessages.add(message);
                iter.remove();
            }
        }

        if (!userMessages.isEmpty()) {
            if (event instanceof MessageEvent messageEvent) {
                event.getBot().sendIRC().message(messageEvent.getChannel().getName(), sender + ", you have postponed messages:");
            } else {
                event.respond(sender + ", you have postponed messages:");
            }
            sendMessagesToUser(sender, userMessages, event);
        }
    }

    private void sendMessagesToUser(String recipient, List<Message> messages, GenericMessageEvent event) {
        int counter = 0;
        for (Message message : messages) {
            String formattedMessage = message.sender + " (" + pTime.format(message.timestamp) + "): " + message.message;
            if (event instanceof MessageEvent messageEvent) {
                if (counter < MAX_MESSAGES_IN_CHANNEL) {
                    event.getBot().sendIRC().message(messageEvent.getChannel().getName(), formattedMessage);
                    counter++;
                } else {
                    event.getBot().sendIRC().message(recipient, formattedMessage);
                }
            } else {
                event.getBot().sendIRC().message(recipient, formattedMessage);
            }
            deleteMessageFromDatabase(message);
        }
        // Add additional check for total number of messages
        if (event instanceof MessageEvent && counter == MAX_MESSAGES_IN_CHANNEL && messages.size() > MAX_MESSAGES_IN_CHANNEL) {
            MessageEvent messageEvent = (MessageEvent) event;
            event.getBot().sendIRC().message(messageEvent.getChannel().getName(), "The remaining messages were sent via DM");
        }
    }

    private int getTotalMessagesForUser(String recipient) throws SQLException {
        String sql = "SELECT count(*) as total FROM tellnew WHERE recipient = ?";
        PreparedStatement statement = db.prepareStatement(sql);
        statement.setString(1, recipient);
        ResultSet rs = statement.executeQuery();
        return rs.next() ? rs.getInt("total") : 0;
    }

    private int getTotalMessagesFromUserToUser(String sender, String recipient) throws SQLException {
        String sql = "SELECT count(*) as total FROM tellnew WHERE sender = ? AND recipient = ?";
        PreparedStatement statement = db.prepareStatement(sql);
        statement.setString(1, sender);
        statement.setString(2, recipient);
        ResultSet rs = statement.executeQuery();
        return rs.next() ? rs.getInt("total") : 0;
    }

    private void saveMessageToDatabase(String sender, String recipient, String message, Timestamp timestamp, String server, String channel) throws SQLException {
        String sql = "INSERT INTO tellnew (sender, recipient, message, timestamp, server, channel) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement statement = db.prepareStatement(sql);
        statement.setString(1, sender);
        statement.setString(2, recipient);
        statement.setString(3, message);
        statement.setTimestamp(4, timestamp);
        statement.setString(5, server);
        statement.setString(6, channel);
        statement.execute();
    }

    private void deleteMessageFromDatabase(Message message) {
        try {
            String sql = "DELETE FROM tellnew WHERE sender = ? AND recipient = ? AND message = ? AND timestamp = ?";
            PreparedStatement statement = db.prepareStatement(sql);
            statement.setString(1, message.sender);
            statement.setString(2, message.recipient);
            statement.setString(3, message.message);
            statement.setTimestamp(4, message.timestamp);
            statement.execute();
        } catch (SQLException e) {
            logger.error("An error occurred", e);
        }
    }
}
