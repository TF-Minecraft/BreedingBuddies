package net.tfminecraft.breedingbuddies;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockbukkit.mockbukkit.matcher.sound.SoundReceiverSoundHeardMatcher.hasHeard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EffectsAndHelpersTest extends PluginTestBase {

    @Test
    void animalSoundsAreHeardByNearbyPlayers() {
        PlayerMock listener = player();
        Cow cow = spawn(Cow.class);
        Horse foal = spawn(Horse.class);

        SoundManager.playAmethystStepSound(cow);
        SoundManager.playAmethystClusterStepSound(cow);
        SoundManager.playAngryCowSound(cow);
        SoundManager.playFoalScream(foal);

        assertThat(listener, hasHeard("block.amethyst_block.step"));
        assertThat(listener, hasHeard(Sound.BLOCK_AMETHYST_CLUSTER_STEP));
        assertThat(listener, hasHeard("entity.cow.ambient"));
        assertThat(listener, hasHeard(Sound.ENTITY_HORSE_DEATH));
    }

    @Test
    void playerFeedbackSoundsArePlayedToThePlayer() {
        PlayerMock player = player();

        SoundManager.playBookOpenSound(player);
        SoundManager.playExperienceSound(player);
        SoundManager.playSignSound(player);

        assertThat(player, hasHeard(Sound.ITEM_BOOK_PAGE_TURN));
        assertThat(player, hasHeard(Sound.ENTITY_EXPERIENCE_ORB_PICKUP));
        assertThat(player, hasHeard(Sound.BLOCK_HANGING_SIGN_PLACE));
    }

    @Test
    void entitiesThatCannotMakeSoundsOrParticlesAreSkipped() {
        Entity item = mock(Entity.class);

        SoundManager.playAmethystStepSound(item);
        SoundManager.playAmethystClusterStepSound(item);
        SoundManager.playAngryCowSound(item);
        SoundManager.playFoalScream(item);
        ParticleManager.heartParticles(item);
        ParticleManager.cherryParticles(item);

        verifyNoInteractions(item);
    }

    @Test
    void particlesAppearJustAboveTheAnimal() {
        LivingEntity cow = mock(LivingEntity.class);
        World mockWorld = mock(World.class);
        when(cow.getWorld()).thenReturn(mockWorld);
        when(cow.getLocation()).thenReturn(new Location(mockWorld, 1, 64, 3));

        ParticleManager.heartParticles(cow);
        ParticleManager.cherryParticles(cow);

        verify(mockWorld).spawnParticle(Particle.HEART, 1.0, 65.0, 3.0, 20, 0.5, 0.5, 0.5, 0.1);
        verify(mockWorld).spawnParticle(Particle.CHERRY_LEAVES, 1.0, 65.0, 3.0, 10, 0.5, 0.5, 0.5, 0.1);
    }

    @Test
    void farmAnimalsAreTheSpeciesListedInTheBundleConfig() throws Exception {
        assertTrue(AnimalUtils.isFarmAnimal(spawn(Cow.class)));
        assertTrue(AnimalUtils.isFarmAnimal(spawn(Horse.class)));
        assertFalse(AnimalUtils.isFarmAnimal(spawn(Pig.class)));

        YamlConfiguration bundles = new YamlConfiguration();
        bundles.loadFromString("PIG:\n  bundlesEnabled: false\nUNICORN:\n  bundlesEnabled: false\n");
        try (LogCapture log = new LogCapture(org.bukkit.Bukkit.getLogger())) {
            new AnimalUtils(bundles);
            assertTrue(log.contains("Invalid entity type in configuration: UNICORN"));
        }

        assertTrue(AnimalUtils.isFarmAnimal(spawn(Pig.class)));
        assertFalse(AnimalUtils.isFarmAnimal(spawn(Cow.class)), "species removed on reload stop counting");
    }

    @Test
    void onlyEmptyStorageSlotsCountAsSpace() {
        PlayerMock player = player();
        assertEquals(36, InventoryUtils.countEmptySlots(player));

        player.getInventory().setItem(0, new ItemStack(Material.STONE));
        assertEquals(35, InventoryUtils.countEmptySlots(player));

        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }
        assertEquals(0, InventoryUtils.countEmptySlots(player), "empty armour slots cannot take a bundle");
    }

    @Test
    void animalMenusKnowWhichAnimalTheyShow() {
        java.util.UUID animal = java.util.UUID.randomUUID();

        AnimalInventoryHolder holder = new AnimalInventoryHolder(animal, 27, "Animal Info");

        assertEquals(animal, holder.getEntityUUID());
        assertSame(holder, holder.getInventory().getHolder());
        assertEquals(27, holder.getInventory().getSize());
    }
}
