package moe.herz;

import org.pircbotx.hooks.types.GenericMessageEvent;

import java.sql.*;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.AbstractMap.SimpleEntry;

public class ReminderHandler {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\.in\\s+(\\d+)([smh])\\s+(.*)$");
    private final PriorityQueue<Long> reminderQueue;
    private final HashMap<Long, Instant> reminderTimes;

    private final Connection dbConnection;

    public ReminderHandler(Connection dbConnection) {
        this.dbConnection = dbConnection;
        this.reminderQueue = new PriorityQueue<>();
        this.reminderTimes = new HashMap<>();
    }

    public void init() {
        // This method is called on bot startup. It should load any existing reminders
        // from the database and add them to reminderQueue and reminderTimes.
        try (Statement stmt = dbConnection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, remind_at FROM UndineReminder");
            while (rs.next()) {
                long id = rs.getLong(1);
                Instant remindAt = rs.getTimestamp(2).toInstant();
                reminderQueue.add(id);
                reminderTimes.put(id, remindAt);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void processReminderRequest(String sender, String message,String channel, GenericMessageEvent event) {
        Matcher matcher = DURATION_PATTERN.matcher(message);
        if (matcher.matches()) {
            int durationValue = Integer.parseInt(matcher.group(1));
            String durationType = matcher.group(2);
            String reminderMessage = matcher.group(3);

            Instant remindAt = switch (durationType) {
                case "s" -> Instant.now().plus(Duration.ofSeconds(durationValue));
                case "m" -> Instant.now().plus(Duration.ofMinutes(durationValue));
                case "h" -> Instant.now().plus(Duration.ofHours(durationValue));
                default -> {
                    event.respond("Invalid duration type. Please use s, m, or h for seconds, minutes, and hours.");
                    yield null;
                }
            };

            addReminder(sender, reminderMessage, remindAt, channel);
            event.respond("Okay, I will remind you in " + durationValue + " " + (durationType.equals("s") ? "seconds" : durationType.equals("m") ? "minutes" : "hours") + ".");
        } else {
            event.respond("Invalid command format. Please use .in [duration][s|m|h] [message].");
        }
    }


    public void addReminder(String sender, String message, Instant remindAt, String channel) {
        try (PreparedStatement pstmt = dbConnection.prepareStatement("INSERT INTO UndineReminder (sender, message, remind_at, channel) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.setTimestamp(3, Timestamp.from(remindAt));
            pstmt.setString(4, channel);
            pstmt.executeUpdate();

            // Get the auto-generated id of the inserted row
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                long id = rs.getLong(1);
                reminderQueue.add(id);
                reminderTimes.put(id, remindAt);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Long peekNextReminder() {
        // Returns the ID of the next reminder to be sent
        return (reminderQueue.peek() != null) ? reminderQueue.peek() : null;
    }

    public Instant getReminderTime(long reminderId) {
        // Returns the time when a specific reminder is scheduled to be sent
        return reminderTimes.get(reminderId);
    }

    public SimpleEntry<String, String> fetchReminder(long reminderId) {
        // Fetch the actual message for a given reminder from the database
        String reminderMessage = null;
        String channel = null;
        try (PreparedStatement pstmt = dbConnection.prepareStatement("SELECT sender, message, channel FROM UndineReminder WHERE id = ?")) {
            pstmt.setLong(1, reminderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String sender = rs.getString(1);
                String message = rs.getString(2);
                channel = rs.getString(3);
                reminderMessage = sender + ": " + message;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new SimpleEntry<>(reminderMessage, channel);
    }

    public void removeReminder(long reminderId) {
        // Removes a reminder from the queue, the times map, and the database
        reminderQueue.remove(reminderId);
        reminderTimes.remove(reminderId);
        try (PreparedStatement pstmt = dbConnection.prepareStatement("DELETE FROM UndineReminder WHERE id = ?")) {
            pstmt.setLong(1, reminderId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
