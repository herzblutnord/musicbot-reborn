package moe.herz;

import java.time.Instant;

public class Reminder implements Comparable<Reminder> {
    long id;
    Instant time;

    public Reminder(long id, Instant time) {
        this.id = id;
        this.time = time;
    }

    public long getId() {
        return id;
    }

    public Instant getTime() {
        return time;
    }

    @Override
    public int compareTo(Reminder other) {
        return time.compareTo(other.getTime());
    }
}

