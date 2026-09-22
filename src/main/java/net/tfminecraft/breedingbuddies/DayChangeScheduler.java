package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class DayChangeScheduler extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final int targetHour;
    private final int targetMinute;
    private LocalTime lastCheckTime;
    private int taskId;

    public DayChangeScheduler(JavaPlugin plugin, int targetHour, int targetMinute) {
        this.plugin = plugin;
        this.targetHour = targetHour;
        this.targetMinute = targetMinute;
        this.lastCheckTime = LocalTime.now(ZoneId.systemDefault());
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @Override
    public void run() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
//        Bukkit.broadcastMessage("Current time: " + now.toString());
        if (now.getHour() == targetHour && now.getMinute() == targetMinute 
                && (lastCheckTime.getMinute() != targetMinute)) {
//            Bukkit.broadcastMessage("DayChangeEvent is being called.");
            plugin.getServer().getPluginManager().callEvent(new DayChangeEvent());
        }
        lastCheckTime = now;
    }

    public void startScheduler() {
        this.taskId = this.runTaskTimer(plugin, 0L, 1200L).getTaskId();
        //this.runTaskTimer(plugin, 0L, 1200L);
    }
}
