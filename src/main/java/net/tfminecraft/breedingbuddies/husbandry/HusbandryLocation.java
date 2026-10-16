package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

public final class HusbandryLocation {

    private HusbandryLocation() {}

    public static void remember(HusbandryAnimal animal, Entity entity) {
        if (animal == null || entity == null) {
            return;
        }
        World world = entity.getWorld();
        if (world == null) {
            return;
        }
        Location location = entity.getLocation();
        animal.setLastLocation(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
