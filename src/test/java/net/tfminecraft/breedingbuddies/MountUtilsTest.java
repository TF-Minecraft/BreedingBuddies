package net.tfminecraft.breedingbuddies;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SkeletonHorse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MountUtilsTest extends PluginTestBase {

    private Horse horse(double health, double speed, double jump) {
        Horse horse = spawn(Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        horse.setHealth(Math.min(health, horse.getHealth()));
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        horse.setJumpStrength(jump);
        return horse;
    }

    @Test
    void speciesStatsComeFromTheBundleConfigWhateverTheCase() {
        MountStats stats = MountStats.getMountStats("horse");

        assertEquals(15, stats.getMinHealth());
        assertEquals(30, stats.getMaxHealth());
        assertEquals(0.1125, stats.getMinSpeed());
        assertEquals(0.3375, stats.getMaxSpeed());
        assertEquals(0.4, stats.getMinJump());
        assertEquals(1.0, stats.getMaxJump());
    }

    @Test
    void statsFallBackPerValueToConfiguredDefaultsAndThenBuiltInDefaults() throws Exception {
        YamlConfiguration bundles = new YamlConfiguration();
        bundles.loadFromString("DONKEY:\n  mountStats:\n    maxHealth: 40\n");
        YamlConfiguration numbers = new YamlConfiguration();
        numbers.loadFromString("defaultMountStats:\n  minSpeed: 0.2\n");
        MountStats.setConfigs(bundles, numbers);

        MountStats donkey = MountStats.getMountStats("DONKEY");
        assertEquals(40, donkey.maxHealth);
        assertEquals(0.2, donkey.minSpeed);
        assertEquals(MountStats.defMinHealth, donkey.minHealth);
        assertEquals(MountStats.defMaxSpeed, donkey.maxSpeed);
        assertEquals(MountStats.defMinJump, donkey.minJump);
        assertEquals(MountStats.defMaxJump, donkey.maxJump);

        MountStats.setConfigs(bundles, new YamlConfiguration());
        MountStats unknown = MountStats.getMountStats("CAMEL");
        assertEquals(MountStats.defMinHealth, unknown.getMinHealth());
        assertEquals(MountStats.defMinSpeed, unknown.getMinSpeed());
    }

    @Test
    void onlyHorseLikeEntitiesCarryMountStats() {
        Horse horse = horse(20, 0.2, 0.7);
        Cow cow = spawn(Cow.class);

        assertTrue(MountUtils.isFullStatMount(horse));
        assertFalse(MountUtils.isFullStatMount(cow));
        assertEquals(0.7, MountUtils.getJumpStrength(horse), 1e-9);
        assertEquals(-1, MountUtils.getJumpStrength(cow));

        MountUtils.setJumpStrength(cow, 0.9);
        MountUtils.setJumpStrength(horse, 0.9);
        assertEquals(0.9, horse.getJumpStrength(), 1e-9);
    }

    @Test
    void healthAndSpeedAreReadAndWrittenThroughAttributes() {
        Horse horse = horse(30, 0.2, 0.7);
        horse.setHealth(30);

        MountUtils.setMaxHealth(horse, 22);
        MountUtils.setSpeed(horse, 0.25);

        assertEquals(22, MountUtils.getMaxHealth(horse));
        assertEquals(22, horse.getHealth(), "current health is capped at the new maximum");
        assertEquals(0.25, MountUtils.getSpeed(horse));
    }

    @Test
    void entitiesWithoutTheAttributesAreLeftAloneAndReportMinusOne() {
        LivingEntity entity = mock(LivingEntity.class);

        MountUtils.setMaxHealth(entity, 20);
        MountUtils.setSpeed(entity, 0.3);

        assertEquals(-1, MountUtils.getMaxHealth(entity));
        assertEquals(-1, MountUtils.getSpeed(entity));
        verify(entity, never()).setHealth(org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void displayConversionsMatchVanillaUnits() {
        assertEquals(15, MountUtils.healthPointsToHearts(31));
        assertEquals(9.486, MountUtils.speedToBlocksPerSecond(0.225), 1e-9);
        assertEquals(1.6201, MountUtils.jumpStrengthToBlockHeight(0.5), 1e-3);
        assertEquals(5.2923, MountUtils.jumpStrengthToBlockHeight(1.0), 1e-3);
    }

    @Test
    void breedingTwoTopParentsAlwaysGivesTheTopValue() {
        for (int i = 0; i < 50; i++) {
            assertEquals(30, MountUtils.breedAttribute(30, 30, 15, 30), 1e-9);
        }
    }

    @Test
    void breedingTwoWeakParentsStaysInTheBottomQuarter() {
        for (int i = 0; i < 200; i++) {
            double child = MountUtils.breedAttribute(15, 15, 15, 30);
            assertTrue(child >= 15 && child <= 15 + 0.25 * 15, "child health " + child);
        }
    }

    @Test
    void bredValuesStayWithinTheSpeciesRange() {
        for (int i = 0; i < 200; i++) {
            double child = MountUtils.breedAttribute(0.12, 0.33, 0.1125, 0.3375);
            assertTrue(child >= 0.1125 && child <= 0.3375, "child speed " + child);
        }
    }

    @Test
    void foalsInheritTheirParentsCappedStats() {
        Horse mother = horse(60, 0.9, 1.0);
        Horse father = horse(30, 0.3375, 1.0);
        Horse foal = horse(15, 0.1125, 0.4);

        MountUtils.applyInheritedStats(foal, mother, father);

        assertEquals(30, MountUtils.getMaxHealth(foal), 1e-9);
        assertEquals(0.3375, MountUtils.getSpeed(foal), 1e-9);
        assertEquals(1.0, foal.getJumpStrength(), 1e-9);
    }

    @Test
    void inheritanceNeedsHorseLikeParents() {
        Horse mother = horse(30, 0.3375, 1.0);
        Cow father = spawn(Cow.class);
        Horse foal = horse(15, 0.1125, 0.4);

        MountUtils.applyInheritedStats(foal, mother, father);

        assertEquals(15, MountUtils.getMaxHealth(foal));
        assertEquals(0.1125, MountUtils.getSpeed(foal));
        assertEquals(0.4, foal.getJumpStrength(), 1e-9);
    }

    @Test
    void geneticsAverageTheNormalisedStats() {
        assertEquals(10_000, MountUtils.calculateGeneticsFromStats(30, 0.3375, 1.0, "HORSE"));
        assertEquals(0, MountUtils.calculateGeneticsFromStats(15, 0.1125, 0.4, "HORSE"));
        assertEquals(5_000, MountUtils.calculateGeneticsFromStats(22.5, 0.225, 0.7, "HORSE"));
        assertEquals(10_000, MountUtils.calculateGeneticsFromStats(99, 9, 9, "HORSE"), "out-of-range stats are capped");
        assertEquals(0, MountUtils.calculateGeneticsFromStats(1, 0, 0, "HORSE"));
    }

    @Test
    void namedMountsAreRatedAgainstTheirSpeciesStats() throws Exception {
        YamlConfiguration bundles = new YamlConfiguration();
        bundles.loadFromString("SKELETON_HORSE:\n  mountStats:\n    minHealth: 10\n    maxHealth: 20\n"
                + "    minSpeed: 0.1\n    maxSpeed: 0.2\n    minJump: 0.5\n    maxJump: 0.9\n");
        MountStats.setConfigs(bundles, new YamlConfiguration());
        SkeletonHorse mount = spawn(SkeletonHorse.class);
        mount.setCustomName("Bones");
        mount.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
        mount.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2);
        mount.setJumpStrength(0.9);

        assertEquals(10_000, MountUtils.getGenetics(mount));
    }

    @Test
    void neuteringIsStoredOnTheEntityAndLegacyTagsStillCount() {
        Horse horse = spawn(Horse.class);
        assertFalse(MountUtils.isNeutered(horse));

        MountUtils.setNeuteredFlag(horse, true);
        assertTrue(MountUtils.isNeutered(horse));

        MountUtils.setNeuteredFlag(horse, false);
        assertFalse(MountUtils.isNeutered(horse));

        horse.addScoreboardTag("ho.isNeutered");
        assertTrue(MountUtils.isNeutered(horse));
    }
}
