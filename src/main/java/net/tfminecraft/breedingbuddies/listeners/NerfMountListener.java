package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

public class NerfMountListener implements Listener {
    @EventHandler
    public void onHorseSpawn(CreatureSpawnEvent event) {
        if (!Numbers.nerfMountStats) return;

        if (!(event.getEntity() instanceof AbstractHorse horse)) return;

        // Solo nerfear si se genera de forma natural o con huevo
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            nerf(horse);
        }

        // marca como ya procesados los spawneados por comando
        else if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
            setNerfedFlag(horse, true); // solo marcador, sin nerfear
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!Numbers.nerfMountStats) return;

        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof AbstractHorse horse)) continue;
            if (event.isNewChunk()) {
                nerf(horse);
            } else if (!isNerfed(horse)) {
                nerf(horse);
            }
        }
    }

    private void nerf(AbstractHorse horse) {
        // Evita nerfeo múltiple
        if (isNerfed(horse)) return;

        // Marca como nerfeado
        setNerfedFlag(horse, true);

        // Asegura valores seguros y aplica divisor
        try {
            AttributeInstance health = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            AttributeInstance speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);

            if (health != null) {
                double newHealth = Math.max(Numbers.minMountHealth, health.getBaseValue() / Numbers.mountNerfDivisor);
                health.setBaseValue(newHealth);
            }

            if (speed != null) {
                double newSpeed = Math.max(Numbers.minMountSpeed, speed.getBaseValue() / Numbers.mountNerfDivisor);
                speed.setBaseValue(newSpeed);
            }

            double newJump = Math.max(Numbers.minMountJump, horse.getJumpStrength() / Numbers.mountNerfDivisor);
            horse.setJumpStrength(newJump);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[BreedinBuddies] Error while nerfing horse: " + e.getMessage());
        }
    }

    public void setNerfedFlag(Entity entity, boolean isNerfed) {
        NamespacedKey key = new NamespacedKey(BreedingBuddies.getInstance(), "is_nerfed");
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (isNerfed ? 1 : 0));
    }

    public boolean isNerfed(Entity entity) {
        NamespacedKey key = new NamespacedKey(BreedingBuddies.getInstance(), "is_nerfed");
        Byte value = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == 1;
    }
}
