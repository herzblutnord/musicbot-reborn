package moe.herz;

import org.pircbotx.PircBotX;

import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;

public class ReminderSender implements Runnable {
    private final ReminderHandler reminderHandler;
    private final PircBotX bot;

    public ReminderSender(ReminderHandler reminderHandler, PircBotX bot) {
        this.reminderHandler = reminderHandler;
        this.bot = bot;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Long nextReminderId = reminderHandler.peekNextReminder();
                if (nextReminderId == null) { // Now this condition can be true
                    Thread.sleep(1000);
                    continue;
                }
                Instant nextReminderTime = reminderHandler.getReminderTime(nextReminderId);
                if (nextReminderTime == null) {
                    Thread.sleep(1000);
                    continue;
                }
                if (nextReminderTime.isBefore(Instant.now())) {
                    SimpleEntry<String, String> reminder = reminderHandler.fetchReminder(nextReminderId);
                    bot.sendIRC().message(reminder.getValue(), reminder.getKey());
                    reminderHandler.removeReminder(nextReminderId);
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
