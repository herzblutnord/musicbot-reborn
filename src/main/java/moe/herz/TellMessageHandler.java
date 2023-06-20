package moe.herz;

import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Locale;

import org.ocpsoft.prettytime.PrettyTime;
import org.pircbotx.hooks.events.MessageEvent;

public class TellMessageHandler {

    private static final int MAX_UNSENT_MESSAGES = 5;
    private static final int MAX_RECEIVED_MESSAGES = 10;

    private final Map<String, LinkedList<String>> unsentMessages = new HashMap<>();
    private final Map<String, Integer> messagesToReceive = new HashMap<>();

    private final Connection db;
    private final PrettyTime p;

    public TellMessageHandler(Connection db) {
        this.db = db;
        this.p = new PrettyTime(Locale.ENGLISH);

        try {
            PreparedStatement st = this.db.prepareStatement("SELECT * FROM tellNew");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                String recipient = rs.getString("recipient");
                messagesToReceive.put(recipient, messagesToReceive.getOrDefault(recipient, 0) + 1);

                PreparedStatement st2 = this.db.prepareStatement("SELECT * FROM tellNew WHERE recipient = ?");
                st2.setString(1, recipient);
                ResultSet rs2 = st2.executeQuery();
                LinkedList<String> messages = new LinkedList<>();
                while (rs2.next()) {
                    String sender = rs2.getString("sender");
                    String message = rs2.getString("message");
                    Timestamp timestamp = rs2.getTimestamp("timestamp");
                    String relativeTimestamp = p.format(timestamp);
                    messages.add(sender + " (" + relativeTimestamp + "): " + message);
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

            unsentMessages.computeIfAbsent(recipient, k -> new LinkedList<>()).add(sender + " (Just now): " + message);
            messagesToReceive.put(recipient, messagesToReceive.getOrDefault(recipient, 0) + 1);

            try {
                PreparedStatement st = db.prepareStatement("INSERT INTO tellNew (sender, recipient, message, timestamp) VALUES (?, ?, ?, ?)");
                st.setString(1, sender);
                st.setString(2, recipient);
                st.setString(3, message);
                st.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                st.executeUpdate();
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
                PreparedStatement st = db.prepareStatement("DELETE FROM tellNew WHERE recipient = ?");
                st.setString(1, sender);
                st.executeUpdate();
                st.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
