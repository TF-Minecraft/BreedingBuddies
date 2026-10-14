package net.tfminecraft.breedingbuddies;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.breedingbuddies.husbandry.HusbandryAnimalsCommand;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryBreedListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryCareListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryDamageListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryDeathListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryHarvestListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryIdentity;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryInspectListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryLifecycleListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryLoader;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryLocator;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryMountListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryNeuterListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRepository;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryTamingListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryTickTask;
import net.tfminecraft.cooking.Cooking;

public class BreedingBuddies extends JavaPlugin {
    public static BreedingBuddies plugin;

    private final HusbandryLoader husbandryLoader = new HusbandryLoader();
    private HusbandryRepository husbandryRepository;

    @Override
    public void onEnable() {
        if (cookingRunsLivestock()) {
            getLogger().severe("Cooking still runs livestock. Set enabled: false in plugins/Cooking/husbandry.yml"
                    + " and restart; BreedingBuddies stays off until then.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        plugin = this;

        new File(getDataFolder(), "Data").mkdirs();
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
        loadConfigs();
        openDatabase();
        HusbandryLifecycleListener.applyStatsRevision();
        HusbandryIdentity.start(this, husbandryRepository);
        registerListeners();

        Bukkit.getScheduler().runTask(this, () -> {
            HusbandryLifecycleListener.resumeLoadedWorlds();
            HusbandryTickTask.start();
            HusbandryLocator.scanUnloaded();
        });

        BreedingBuddiesCommand admin = new BreedingBuddiesCommand();
        getCommand("breedingbuddies").setExecutor(admin);
        getCommand("breedingbuddies").setTabCompleter(admin);
        HusbandryAnimalsCommand animals = new HusbandryAnimalsCommand();
        getCommand("animals").setExecutor(animals);
        getCommand("animals").setTabCompleter(animals);
    }

    @Override
    public void onDisable() {
        if (plugin != this) return;
        HusbandryTickTask.stop();
        HusbandryLifecycleListener.flushLoadedForDisable();
        closeDatabase();
    }

    /** True while Cooking's own husbandry is on; both must never run the same animals. */
    static boolean cookingRunsLivestock() {
        return Cooking.plugin != null && Cooking.plugin.isEnabled() && Cooking.plugin.isHusbandryEnabled();
    }

    public void loadConfigs() {
        husbandryLoader.load(new File(getDataFolder(), "config.yml"));
    }

    public void reloadAll() {
        loadConfigs();
        HusbandryLifecycleListener.applyStatsRevision();
        HusbandryTickTask.stop();
        HusbandryTickTask.start();
    }

    public HusbandryRepository getHusbandryRepository() {
        return husbandryRepository;
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new HusbandryLifecycleListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryCareListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryTamingListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryDeathListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryHarvestListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryBreedListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryNeuterListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryInspectListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryMountListener(), this);
        getServer().getPluginManager().registerEvents(new HusbandryDamageListener(), this);
    }

    private void openDatabase() {
        File dbFile = new File(new File(getDataFolder(), "Data"), "husbandry.db");
        try {
            husbandryRepository = HusbandryRepository.open(dbFile);
        } catch (RuntimeException ex) {
            getLogger().severe("Failed to open the animal database: " + ex.getMessage());
            throw ex;
        }
    }

    private void closeDatabase() {
        if (husbandryRepository == null) {
            return;
        }
        try {
            husbandryRepository.close();
        } catch (RuntimeException ex) {
            getLogger().warning("Failed to close the animal database: " + ex.getMessage());
        } finally {
            husbandryRepository = null;
        }
    }
}
