package net.tfminecraft.breedingbuddies.husbandry;

import java.util.UUID;

public final class HusbandryAnimal {

    private UUID uuid;
    private String type;
    private String name;
    private HusbandryAnimalState state;
    private int genetics;
    private int care;
    private Long hungrySince;
    private Long dirtySince;
    private long lastProcessedAt;
    private Long unloadedAt;
    private double afflictionElapsed;
    private double afflictionAt;
    private Long lastMilkAt;
    private Long woolReadyAt;
    private boolean neutered;
    private Long loadedVisitStart;
    private Long matureAt;
    private Long shedReadyAt;
    private Long eggReadyAt;
    private Long lastPetAt;
    private long careUpRemainderSeconds;
    private long careDownRemainderSeconds;
    private String statsRevision;
    private String world;
    private Integer x;
    private Integer y;
    private Integer z;

    public HusbandryAnimal(UUID uuid, String type, String name) {
        this.uuid = uuid;
        this.type = type;
        this.name = sanitizeName(name);
        this.state = HusbandryAnimalState.UNTAMED;
        this.lastProcessedAt = System.currentTimeMillis();
    }

    public UUID uuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitizeName(name);
    }

    public HusbandryAnimalState state() {
        return state;
    }

    public void setState(HusbandryAnimalState state) {
        this.state = state == null ? HusbandryAnimalState.UNTAMED : state;
    }

    public int genetics() {
        return genetics;
    }

    public void setGenetics(int genetics) {
        this.genetics = genetics;
    }

    public int care() {
        return care;
    }

    public void setCare(int care) {
        this.care = care;
    }

    public Long hungrySince() {
        return hungrySince;
    }

    public void setHungrySince(Long hungrySince) {
        this.hungrySince = hungrySince;
    }

    public Long dirtySince() {
        return dirtySince;
    }

    public void setDirtySince(Long dirtySince) {
        this.dirtySince = dirtySince;
    }

    public long lastProcessedAt() {
        return lastProcessedAt;
    }

    public void setLastProcessedAt(long lastProcessedAt) {
        this.lastProcessedAt = lastProcessedAt;
    }

    public Long unloadedAt() {
        return unloadedAt;
    }

    public void setUnloadedAt(Long unloadedAt) {
        this.unloadedAt = unloadedAt;
    }

    public double afflictionElapsed() {
        return afflictionElapsed;
    }

    public void setAfflictionElapsed(double afflictionElapsed) {
        this.afflictionElapsed = afflictionElapsed;
    }

    public double afflictionAt() {
        return afflictionAt;
    }

    public void setAfflictionAt(double afflictionAt) {
        this.afflictionAt = afflictionAt;
    }

    public Long lastMilkAt() {
        return lastMilkAt;
    }

    public void setLastMilkAt(Long lastMilkAt) {
        this.lastMilkAt = lastMilkAt;
    }

    public Long woolReadyAt() {
        return woolReadyAt;
    }

    public void setWoolReadyAt(Long woolReadyAt) {
        this.woolReadyAt = woolReadyAt;
    }

    public boolean neutered() {
        return neutered;
    }

    public void setNeutered(boolean neutered) {
        this.neutered = neutered;
    }

    public Long loadedVisitStart() {
        return loadedVisitStart;
    }

    public void setLoadedVisitStart(Long loadedVisitStart) {
        this.loadedVisitStart = loadedVisitStart;
    }

    public Long matureAt() {
        return matureAt;
    }

    public void setMatureAt(Long matureAt) {
        this.matureAt = matureAt;
    }

    public Long shedReadyAt() {
        return shedReadyAt;
    }

    public void setShedReadyAt(Long shedReadyAt) {
        this.shedReadyAt = shedReadyAt;
    }

    public Long eggReadyAt() {
        return eggReadyAt;
    }

    public void setEggReadyAt(Long eggReadyAt) {
        this.eggReadyAt = eggReadyAt;
    }

    public Long lastPetAt() {
        return lastPetAt;
    }

    public void setLastPetAt(Long lastPetAt) {
        this.lastPetAt = lastPetAt;
    }

    public long careUpRemainderSeconds() {
        return careUpRemainderSeconds;
    }

    public void setCareUpRemainderSeconds(long careUpRemainderSeconds) {
        this.careUpRemainderSeconds = Math.max(0, careUpRemainderSeconds);
    }

    public long careDownRemainderSeconds() {
        return careDownRemainderSeconds;
    }

    public void setCareDownRemainderSeconds(long careDownRemainderSeconds) {
        this.careDownRemainderSeconds = Math.max(0, careDownRemainderSeconds);
    }

    public String statsRevision() {
        return statsRevision;
    }

    public void setStatsRevision(String statsRevision) {
        if (statsRevision == null || statsRevision.isBlank()) {
            this.statsRevision = null;
            return;
        }
        this.statsRevision = statsRevision.trim();
    }

    public String world() {
        return world;
    }

    public Integer x() {
        return x;
    }

    public Integer y() {
        return y;
    }

    public Integer z() {
        return z;
    }

    public boolean hasLocation() {
        return world != null && !world.isBlank() && x != null && y != null && z != null;
    }

    public void setLastLocation(String world, int x, int y, int z) {
        if (world == null || world.isBlank()) {
            clearLocation();
            return;
        }
        this.world = world.trim();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setStoredLocation(String world, Integer x, Integer y, Integer z) {
        if (world == null || world.isBlank() || x == null || y == null || z == null) {
            clearLocation();
            return;
        }
        setLastLocation(world, x, y, z);
    }

    public void clearLocation() {
        this.world = null;
        this.x = null;
        this.y = null;
        this.z = null;
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank() || "???".equals(name.trim())) {
            return "";
        }
        return name;
    }
}
