package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Sheep;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.breedingbuddies.husbandry.HusbandryHarvest.ShearResult;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRuntimeCoverageTest.Farm;

class HusbandryLivingProductsTest {
    @TempDir Path directory;
    private Farm f;

    @BeforeEach void open() throws Exception {
        f = new Farm(directory);
        for (String species : List.of("PIG", "CHICKEN", "SHEEP")) {
            f.config.set("species." + species + ".shed.drops.common", List.of(Map.of("path", "v.stick", "amount", 1, "weight", 1)));
        }
        f.config.set("species.SHEEP.shear.drops.common", List.of(Map.of("path", "v.white_wool", "amount", 1, "weight", 1)));
        f.config.set("shed-chance", 1.0);
        f.reload();
    }

    @AfterEach void close() throws Exception { f.close(); }

    @Test void pigsRootAndChickensMoultWhenTheyShed() {
        Pig pig = f.spawn(Pig.class); HusbandryAnimal rooting = f.record(pig);
        Chicken chicken = f.spawn(Chicken.class); HusbandryAnimal moulting = f.record(chicken);
        Sheep sheep = f.spawn(Sheep.class); HusbandryAnimal shedding = f.record(sheep);
        long now = System.currentTimeMillis();
        HusbandryShed.tryShed(pig, rooting, now);
        HusbandryShed.tryShed(chicken, moulting, now);
        HusbandryShed.tryShed(sheep, shedding, now);
        assertTrue(rooting.shedReadyAt() > now);
        assertTrue(moulting.shedReadyAt() > now);
        assertTrue(shedding.shedReadyAt() > now);
    }

    @Test void woolExtrasWaitForAHappySheep() {
        Sheep sheep = f.spawn(Sheep.class); HusbandryAnimal animal = f.record(sheep); animal.setDirtySince(1L);
        HusbandrySpecies species = HusbandryConfig.species(EntityType.SHEEP);
        assertEquals(ShearResult.UNWELL, HusbandryHarvest.shearReadiness(animal, species, 0));
        assertEquals(ShearResult.UNWELL, HusbandryHarvest.tryShear(f.player, sheep, animal, species, f.repository, 0));
        assertEquals("§cThis animal needs care before it gives wool.", f.player.nextMessage());
        assertEquals(ShearResult.UNWELL, HusbandryHarvest.tryShear(null, sheep, animal, species, f.repository, 0));
        assertEquals(ShearResult.UNWELL, HusbandryHarvest.trySheepBonusShear(f.player, animal, species, f.repository, 0));
        assertNull(f.player.nextMessage(), "Vanilla wool still comes off; only the extras wait");
    }

    @Test void milkWaitsForAHappyCow() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); animal.setHungrySince(1L);
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        PlayerInteractEntityEvent milking = new PlayerInteractEntityEvent(f.player, cow);
        new HusbandryHarvestListener().onMilk(milking);
        assertTrue(milking.isCancelled());
        assertEquals("§cThis animal needs care before it gives milk.", f.player.nextMessage());
        assertEquals(Material.BUCKET, f.player.getInventory().getItemInMainHand().getType());
        assertNull(animal.lastMilkAt());
        assertTrue(f.world.getEntitiesByClass(Item.class).isEmpty());
    }
}
