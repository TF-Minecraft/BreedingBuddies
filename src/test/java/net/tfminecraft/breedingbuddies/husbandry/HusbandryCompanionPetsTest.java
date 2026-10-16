package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.CatMock;
import org.mockbukkit.mockbukkit.entity.WolfMock;

import net.kyori.adventure.text.Component;

class HusbandryCompanionPetsTest {
    @TempDir Path directory;
    private HusbandryRuntimeCoverageTest.Farm farm;

    @BeforeEach void open() throws Exception {
        farm = new HusbandryRuntimeCoverageTest.Farm(directory);
        new HusbandryLoader().load(new File("src/main/resources/config.yml"));
    }

    @AfterEach void close() throws Exception { farm.close(); }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"WOLF", "CAT"})
    void companionPetsSurviveChunkLoadsAndStartupWhileWildAnimalsAreStillRemoved(EntityType type) {
        Tameable pet = spawn(type);
        pet.setTamed(true);
        pet.setOwner(farm.player);
        pet.setPersistent(true);
        pet.customName(Component.text("Companion"));
        NamespacedKey petKey = new NamespacedKey("companionpets", "pet");
        String petId = UUID.randomUUID().toString();
        // CompanionPets stores its own pet ID here, independently of husbandry ownership.
        pet.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, petId);
        Tameable wild = spawn(type);

        new HusbandryLifecycleListener().onEntitiesLoad(
                new EntitiesLoadEvent(pet.getChunk(), List.of(pet, wild)));

        assertTrue(pet.isValid(), "Chunk cleanup must leave CompanionPets bodies intact");
        assertFalse(wild.isValid(), "Untagged wild animals still follow remove-unowned");

        Tameable otherWild = spawn(type);
        HusbandryLifecycleListener.resumeLoadedWorlds();

        assertTrue(pet.isValid(), "The startup scan must also leave CompanionPets bodies intact");
        assertFalse(otherWild.isValid());
        assertEquals(Component.text("Companion"), pet.customName());
        assertEquals(farm.player.getUniqueId(), pet.getOwner().getUniqueId());
        assertEquals(petId, pet.getPersistentDataContainer().get(petKey, PersistentDataType.STRING));
        assertFalse(HusbandryEntities.isManaged(pet));
        assertFalse(farm.repository.exists(pet.getUniqueId()));
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"WOLF", "CAT"})
    void companionPetsKeepTheirDropsWhileWildAnimalsLoseThem(EntityType type) {
        Tameable pet = spawn(type);
        pet.getPersistentDataContainer().set(
                new NamespacedKey("companionpets", "pet"), PersistentDataType.STRING, UUID.randomUUID().toString());
        Tameable wild = spawn(type);
        HusbandryDeathListener listener = new HusbandryDeathListener();

        EntityDeathEvent petDeath = death(pet);
        listener.onDeath(petDeath);
        EntityDeathEvent wildDeath = death(wild);
        listener.onDeath(wildDeath);

        assertEquals(List.of(new ItemStack(Material.STRING)), petDeath.getDrops());
        assertEquals(5, petDeath.getDroppedExp());
        assertTrue(wildDeath.getDrops().isEmpty(), "Untagged wild animals still follow remove-unowned");
        assertEquals(0, wildDeath.getDroppedExp());
    }

    private static EntityDeathEvent death(Tameable entity) {
        return new EntityDeathEvent(entity, mock(DamageSource.class),
                new ArrayList<>(List.of(new ItemStack(Material.STRING))), 5);
    }

    private Tameable spawn(EntityType type) {
        Location at = new Location(farm.world, 0, 65, 0);
        return type == EntityType.WOLF
                ? farm.world.spawn(at, WolfMock.class)
                : farm.world.spawn(at, CatMock.class);
    }
}
