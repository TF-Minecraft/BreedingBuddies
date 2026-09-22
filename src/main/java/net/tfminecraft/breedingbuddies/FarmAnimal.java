package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.listeners.BundleCollector;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class FarmAnimal {
	private UUID uuid;
    private String name;
    private Set<UUID> ownersUuids = new HashSet<>();
    //private Entity entity;
	private int geneticPoints;
    private AnimalStates state;
    private boolean fed = false;
    private boolean cared = false;
    private int daysOut = 0;
    private boolean sleptInStable = true;
    private boolean spaceNeeded = false;
    private boolean waterNeeded = false;
    private boolean stableNeeded = true;
    private Date lastRewardCollected;
    private Date lastUpdated;

    private static FileConfiguration bundlesConfig;


    public FarmAnimal(UUID uuid, String name, UUID uuidOwner, Entity entity) {
        this.uuid = uuid;
        this.name = name;
        this.ownersUuids.add(uuidOwner);
        this.geneticPoints = new Random().nextInt(Numbers.initialGeneticMax);
        this.state = AnimalStates.HAPPY;
        if (entity instanceof AbstractHorse) {
            calcGeneticsIfIsMount(entity);
            stableNeeded = false;
        }
    }
    
    public FarmAnimal(UUID uuid, String name, UUID uuidOwner, Entity entity, int genetics) {
        this.uuid = uuid;
        this.name = name;
        this.ownersUuids.add(uuidOwner);
        this.state = AnimalStates.HAPPY;
        this.geneticPoints = genetics;
        if (entity instanceof AbstractHorse) {
            calcGeneticsIfIsMount(entity);
            stableNeeded = false;
        }
    }
    
    public FarmAnimal(UUID uuid, String name, UUID uuidOwner, Entity entity, int genetics, int friendship) {
        this.uuid = uuid;
        this.name = "???";
        this.friendshipPoints = friendship;
        this.state = AnimalStates.UNOWNED;
        this.geneticPoints = genetics;
        if (entity instanceof AbstractHorse) {
            calcGeneticsIfIsMount(entity);
            stableNeeded = false;
        }
    }

    public FarmAnimal(UUID uuid, int geneticPoints, Entity entity) {
        this.uuid = uuid;
        this.name = "???";
        this.state = AnimalStates.UNOWNED;
        this.geneticPoints = geneticPoints;
        if (entity instanceof AbstractHorse) {
            calcGeneticsIfIsMount(entity);
            stableNeeded = false;
        }
    }

    public FarmAnimal(UUID uuid, Entity entity) {
        this.uuid = uuid;
        this.name = "???";
        this.state = AnimalStates.UNOWNED;
        if (entity instanceof AbstractHorse) {
            calcGeneticsIfIsMount(entity);
            stableNeeded = false;
        }
    }
    
    public FarmAnimal (UUID animalUUID, String name, Set<UUID> owners) {
    	this.uuid = animalUUID;
    	this.name = name;
    	this.ownersUuids = (Set<UUID>) owners;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<UUID> getOwnersUuids() {
        return ownersUuids;
    }
    public void setOwnersUuids (Set<UUID> newOwners) {
        this.ownersUuids = newOwners;
    }

    public int getFriendshipPoints() {
        return friendshipPoints;
    }

    public void increaseFriendship(int amount) {
        friendshipPoints += amount;
    }

    public void decreaseFriendship(int amount) {
        friendshipPoints -= amount;
        if (friendshipPoints < 0) {
        	friendshipPoints = 0;
        }
    }

    public void changeDay(boolean waterNeeded, boolean spaceNeeded, boolean sleptInStable, int friendshipLost) {
        if (lastUpdated != null) {
            Instant lastInstant = lastUpdated.toInstant();
            Instant now = Instant.now();
            long hoursSinceUpdate = ChronoUnit.HOURS.between(lastInstant, now);

            if (hoursSinceUpdate < 24) {
                return; // Salimos del método sin hacer nada
            }
        }

        if (!spaceNeeded && !waterNeeded && sleptInStable && fed && cared)
            state = AnimalStates.HAPPY;
        else
            state = AnimalStates.SAD;

        this.spaceNeeded = spaceNeeded;
        this.waterNeeded = waterNeeded;
        this.sleptInStable = sleptInStable;
        fed = false;
        cared = false;
        setFriendshipPoints(friendshipPoints - friendshipLost);
        setLastUpdated(new Date());
    }

    public boolean getFed() {
        return fed;
    }

    public void setFed(boolean fed) {
        this.fed = fed;
    }

    public boolean getCared() {
        return cared;
    }

    public void setCared(boolean cared) {
        this.cared = cared;
    }

    public AnimalStates getState() {
        return state;
    }

    public void setState(AnimalStates state) {
        this.state = state;
    }

    public int getGeneticPoints() {
        return geneticPoints;
    }

    public int getDaysOut() {
        return daysOut;
    }

    public void incrementDaysOut() {
        daysOut++;
    }

//    public Entity getEntity() {
//        return entity;
//    }

    public Date getLastRewardCollected() {
        return lastRewardCollected;
    }

    public void setLastRewardCollected(Date lastRewardCollected) {
        this.lastRewardCollected = lastRewardCollected;
    }

    public boolean canCollectReward() {
    	if (state != AnimalStates.HAPPY) {
        	return false;
        }
    	
        if (lastRewardCollected == null) {
            return true;
        }
        
        long timeSinceLastReward = System.currentTimeMillis() - lastRewardCollected.getTime();
        double hoursSinceLastReward = (double) timeSinceLastReward / (1000 * 60 * 60); // Convertir milisegundos a horas
        return hoursSinceLastReward >= Numbers.hoursBetweenRewards;
    }

    public static void setBundlesConfig(FileConfiguration bundlesConfig) {
        FarmAnimal.bundlesConfig = bundlesConfig;
    }

    public String timeForNextReward(Entity entity) {
        ConfigurationSection animalSection = bundlesConfig.getConfigurationSection(entity.getType().toString());
        if (state != AnimalStates.HAPPY || !animalSection.getBoolean("bundlesEnabled", false)) {
    		return "No bundle";
    	}
        long remainingTimeMillis = timeForNextRewardMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(remainingTimeMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTimeMillis - TimeUnit.HOURS.toMillis(hours));

        String timeString = String.format("%02d:%02d", hours, minutes);
        return timeString;
    }

    private long timeForNextRewardMillis() {
        if (lastRewardCollected == null) {
            return 0;
        }
        long timeSinceLastReward = System.currentTimeMillis() - lastRewardCollected.getTime();
        long remainingTimeMillis = (long) (Numbers.hoursBetweenRewards * 3600000 - timeSinceLastReward);
        return Math.max(remainingTimeMillis, 0);
    }

    void calcGeneticsIfIsMount(Entity entity)
    {
        if (MountUtils.isFullStatMount(entity))
            this.geneticPoints = MountUtils.getGenetics(entity);
    }

	public void resetDaysOut() {
		daysOut = 0;
	}
	
	private int friendshipPoints = 0;
    public void setFriendshipPoints(int friendshipPoints) {
        this.friendshipPoints = Math.max(friendshipPoints, 0);
    }

	public void setGeneticPoints(int geneticPoints) {
		this.geneticPoints = geneticPoints;
	}

	public void setDaysOut(int daysOut) {
		this.daysOut = daysOut;
	}

    public void setNeutered(Entity entity) {
        entity.getScoreboardTags().add("bb.isNeutered");
    }
    public boolean isNeutered(Entity entity) {
        return entity.getScoreboardTags().contains("bb.isNeutered");
    }
    public boolean isSleptInStable() {
        return sleptInStable;
    }

    public void setSleptInStable(boolean sleptInStable) {
        this.sleptInStable = sleptInStable;
    }

    public boolean isSpaceNeeded() {
        return spaceNeeded;
    }

    public void setSpaceNeeded(boolean spaceNeeded) {
        this.spaceNeeded = spaceNeeded;
    }

    public boolean isWaterNeeded() {
        return waterNeeded;
    }

    public void setWaterNeeded(boolean waterNeeded) {
        this.waterNeeded = waterNeeded;
    }

    public boolean isStableNeeded() {
        return stableNeeded;
    }

    public void setStableNeeded(boolean stableNeeded) {
        this.stableNeeded = stableNeeded;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}