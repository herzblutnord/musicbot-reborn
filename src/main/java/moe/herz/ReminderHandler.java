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
import java.util.Set;
import java.util.HashSet;


public class ReminderHandler {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\.in\\s+((\\d+[wdhms])+)?\\s+(.*)$");
    private final PriorityQueue<Reminder> reminderQueue;
    private final HashMap<Long, Reminder> reminders;

    private final Connection dbConnection;

    public ReminderHandler(Connection dbConnection) {
        this.dbConnection = dbConnection;
        this.reminderQueue = new PriorityQueue<>();
        this.reminders = new HashMap<>();
    }

    public void init() {
        // This method is called on bot startup. It should load any existing reminders
        // from the database and add them to reminderQueue and reminderTimes.

        // Clear the current reminderQueue and reminderTimes
        reminderQueue.clear();
        reminders.clear();


        try (Statement stmt = dbConnection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, remind_at FROM UndineReminder");
            while (rs.next()) {
                long id = rs.getLong(1);
                Instant remindAt = rs.getTimestamp(2).toInstant();

                // Create a new Reminder instance and add it to the queue and map
                Reminder reminder = new Reminder(id, remindAt);
                reminderQueue.add(reminder);
                reminders.put(id, reminder);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void processReminderRequest(String sender, String message, String channel, GenericMessageEvent event) {
        Matcher matcher = DURATION_PATTERN.matcher(message);
        if (matcher.matches()) {
            String durationString = matcher.group(1);
            String reminderMessage = matcher.group(3);

            if (durationString == null || durationString.isEmpty()) {
                event.getBot().sendIRC().message(channel, "Invalid duration format. Please specify a duration.");
                return;
            }

            Duration duration = Duration.ZERO;

            Pattern durationPattern = Pattern.compile("(\\d+)([wdhms])");
            Matcher durationMatcher = durationPattern.matcher(durationString);
            while (durationMatcher.find()) {
                int durationValue = Integer.parseInt(durationMatcher.group(1));
                String durationType = durationMatcher.group(2);

                switch (durationType) {
                    case "w" -> duration = duration.plus(Duration.ofDays((long) durationValue * 7));
                    case "d" -> duration = duration.plus(Duration.ofDays(durationValue));
                    case "h" -> duration = duration.plus(Duration.ofHours(durationValue));
                    case "m" -> duration = duration.plus(Duration.ofMinutes(durationValue));
                    case "s" -> duration = duration.plus(Duration.ofSeconds(durationValue));
                }
            }

            // Limit the duration to 1 year (365 days)
            if (duration.toDays() > 365) {
                event.getBot().sendIRC().message(channel, "Sorry, the maximum duration for a reminder is 1 year.");
                return;
            }

            Instant remindAt = Instant.now().plus(duration);
            addReminder(sender, reminderMessage, remindAt, channel);

            String readableDuration = getReadableDuration(duration);
            event.getBot().sendIRC().message(channel, "Okay, I will remind you in " + readableDuration + ".");
        } else {
            event.getBot().sendIRC().message(channel, "Invalid command format. Please use .in [duration][w|d|h|m|s] [message].");
        }
    }



    public String getReadableDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long days = seconds / (24 * 60 * 60);
        seconds %= (24 * 60 * 60);
        long hours = seconds / (60 * 60);
        seconds %= (60 * 60);
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder readableDuration = new StringBuilder();

        if(days > 0) {
            readableDuration.append(days).append(" days ");
        }
        if(hours > 0) {
            readableDuration.append(hours).append(" hours ");
        }
        if(minutes > 0) {
            readableDuration.append(minutes).append(" minutes ");
        }
        if(seconds > 0 || readableDuration.length() == 0) {
            readableDuration.append(seconds).append(" seconds");
        }

        return readableDuration.toString().trim();
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

                // create a new Reminder instance and add it to the queue and map
                Reminder reminder = new Reminder(id, remindAt);
                reminderQueue.add(reminder);
                reminders.put(id, reminder);

                //System.out.println("New reminder added. ID: " + id + ", Time: " + remindAt); // Debug logging
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public Long peekNextReminder() {
        return (reminderQueue.peek() != null) ? reminderQueue.peek().getId() : null;
    }

    public Instant getReminderTime(long reminderId) {
        Reminder reminder = reminders.get(reminderId);
        return (reminder != null) ? reminder.getTime() : null;
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
        Reminder reminder = reminders.get(reminderId);
        reminderQueue.remove(reminder);
        reminders.remove(reminderId);
        try (PreparedStatement pstmt = dbConnection.prepareStatement("DELETE FROM UndineReminder WHERE id = ?")) {
            pstmt.setLong(1, reminderId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void cleanupOldReminders() {
        Instant now = Instant.now();
        Set<Long> ids = new HashSet<>(this.reminders.keySet()); // Create a copy of the key set
        for (Long id : ids) {
            Instant reminderTime = this.getReminderTime(id);
            if (reminderTime != null && reminderTime.isBefore(now)) {
                this.removeReminder(id);
            }
        }
    }
}
