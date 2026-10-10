package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Clock;
import java.time.LocalTime;

public class DayChangeScheduler extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final int targetHour;
    private final int targetMinute;
    private LocalTime lastCheckTime;
    private int taskId;

    public DayChangeScheduler(JavaPlugin plugin, int targetHour, int targetMinute) {
        this(plugin, targetHour, targetMinute, Clock.systemUTC());
    }

    DayChangeScheduler(JavaPlugin plugin, int targetHour, int targetMinute, Clock clock) {
        this.plugin = plugin;
        this.clock = clock;
        this.targetHour = targetHour;
        this.targetMinute = targetMinute;
        this.lastCheckTime = LocalTime.now(clock);
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @Override
    public void run() {
        LocalTime now = LocalTime.now(clock);
        if (now.getHour() == targetHour && now.getMinute() == targetMinute 
                && (lastCheckTime.getMinute() != targetMinute)) {
            plugin.getServer().getPluginManager().callEvent(new DayChangeEvent());
        }
        lastCheckTime = now;
    }

    public void startScheduler() {
        this.taskId = this.runTaskTimer(plugin, 0L, 1200L).getTaskId();
    }
}
