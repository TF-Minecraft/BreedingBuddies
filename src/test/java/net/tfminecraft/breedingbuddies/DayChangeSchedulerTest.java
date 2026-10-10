package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DayChangeSchedulerTest extends PluginTestBase {

    /** A UTC clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now;

        ManualClock(String isoInstant) {
            now = Instant.parse(isoInstant);
        }

        void set(String isoInstant) {
            now = Instant.parse(isoInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static JavaPlugin pluginWith(PluginManager events) {
        JavaPlugin owner = mock(JavaPlugin.class, RETURNS_DEEP_STUBS);
        when(owner.getServer().getPluginManager()).thenReturn(events);
        return owner;
    }

    @Test
    void theDayChangesOnceWhenTheTargetMinuteArrives() {
        PluginManager events = mock(PluginManager.class);
        ManualClock clock = new ManualClock("2026-10-10T20:59:30Z");
        DayChangeScheduler scheduler = new DayChangeScheduler(pluginWith(events), 21, 1, clock);

        scheduler.run();
        clock.set("2026-10-10T21:00:30Z");
        scheduler.run();
        verify(events, never()).callEvent(any(Event.class));

        clock.set("2026-10-10T21:01:30Z");
        scheduler.run();
        verify(events, times(1)).callEvent(any(DayChangeEvent.class));

        clock.set("2026-10-10T21:01:50Z");
        scheduler.run();
        verify(events, times(1)).callEvent(any(DayChangeEvent.class));

        clock.set("2026-10-10T21:02:30Z");
        scheduler.run();
        clock.set("2026-10-11T21:01:10Z");
        scheduler.run();
        verify(events, times(2)).callEvent(any(DayChangeEvent.class));
    }

    @Test
    void startingDuringTheTargetMinuteDoesNotTriggerAChange() {
        PluginManager events = mock(PluginManager.class);
        ManualClock clock = new ManualClock("2026-10-10T21:01:05Z");
        DayChangeScheduler scheduler = new DayChangeScheduler(pluginWith(events), 21, 1, clock);

        clock.set("2026-10-10T21:01:45Z");
        scheduler.run();

        verify(events, never()).callEvent(any(Event.class));
    }

    @Test
    void theRightHourIsNeededAsWellAsTheRightMinute() {
        PluginManager events = mock(PluginManager.class);
        ManualClock clock = new ManualClock("2026-10-10T19:00:30Z");
        DayChangeScheduler scheduler = new DayChangeScheduler(pluginWith(events), 21, 1, clock);

        clock.set("2026-10-10T19:01:30Z");
        scheduler.run();

        verify(events, never()).callEvent(any(Event.class));
    }

    @Test
    void theSchedulerChecksEveryMinuteUntilCancelled() {
        DayChangeScheduler scheduler = new DayChangeScheduler(plugin, 21, 1);

        scheduler.startScheduler();
        int taskId = scheduler.getTaskId();
        assertTrue(server.getScheduler().isQueued(taskId));

        scheduler.cancel();
        assertFalse(server.getScheduler().isQueued(taskId));
    }
}
