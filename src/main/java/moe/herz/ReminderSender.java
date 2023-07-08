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
                Reminder nextReminder = reminderHandler.getNextReminder();
                //System.out.println("Next reminder: " + nextReminder); // Debug logging

                if (nextReminder == null) {
                    Thread.sleep(1000);
                    continue;
                }

                Instant nextReminderTime = nextReminder.getTime();

                if (nextReminderTime.isBefore(Instant.now())) {
                    SimpleEntry<String, String> reminder = reminderHandler.fetchReminder(nextReminder.getId());
                    bot.sendIRC().message(reminder.getValue(), reminder.getKey());
                    reminderHandler.removeReminder(nextReminder.getId());
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

