package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.entity.EntityType;

public final class HusbandryMountStats {

    private final EntityType type;
    private final double minHealth;
    private final double maxHealth;
    private final double minSpeed;
    private final double maxSpeed;
    private final double minJump;
    private final double maxJump;

    public HusbandryMountStats(
            EntityType type,
            double minHealth,
            double maxHealth,
            double minSpeed,
            double maxSpeed,
            double minJump,
            double maxJump) {
        this.type = type;
        this.minHealth = minHealth;
        this.maxHealth = maxHealth;
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.minJump = minJump;
        this.maxJump = maxJump;
    }

    public EntityType type() {
        return type;
    }

    public double minHealth() {
        return minHealth;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double minSpeed() {
        return minSpeed;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double minJump() {
        return minJump;
    }

    public double maxJump() {
        return maxJump;
    }
}
