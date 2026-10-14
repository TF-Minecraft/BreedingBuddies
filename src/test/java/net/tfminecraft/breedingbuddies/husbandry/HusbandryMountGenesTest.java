package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import org.bukkit.entity.Horse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.breedingbuddies.husbandry.HusbandryMountGenes.Stat;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRuntimeCoverageTest.Farm;

class HusbandryMountGenesTest {
    @TempDir Path directory;
    private Farm f;

    @BeforeEach void open() throws Exception {
        f = new Farm(directory);
        f.config.set("max-genetics", 1000); f.config.set("care-max", 200); f.config.set("initial-genetic-max", 20);
        f.config.set("mounts.HORSE", java.util.Map.of("min-health", 10, "max-health", 30, "min-speed", 0.1, "max-speed", 0.3, "min-jump", 0.4, "max-jump", 1.0));
        f.config.set("mounts.speed.min-pct", 0.4); f.config.set("mounts.speed.genetics-pct", 0.5); f.config.set("mounts.speed.care-pct", 0.1);
        f.reload();
    }

    @AfterEach void close() throws Exception { f.close(); }

    @Test void recordsWithoutGenesUseTheirGeneticsAndSettingGenesAveragesThem() {
        HusbandryAnimal old = animal(300, 0);
        for (Stat stat : Stat.values()) assertEquals(300, HusbandryMountGenes.gene(old, stat));
        HusbandryMountGenes.set(old, 900, 600, -5);
        assertEquals(900, HusbandryMountGenes.gene(old, Stat.HEALTH));
        assertEquals(600, HusbandryMountGenes.gene(old, Stat.SPEED));
        assertEquals(0, HusbandryMountGenes.gene(old, Stat.JUMP));
        assertEquals(500, old.genetics());
        HusbandryMountGenes.set(old, 5000, 5000, 5000);
        assertEquals(1000, old.genetics());
    }

    @Test void wildMountsRollEachStatWithinTheWildRange() throws Exception {
        HusbandryAnimal wild = animal(0, 0);
        HusbandryMountGenes.rollWild(wild, new Random(7));
        for (Stat stat : Stat.values()) assertTrue(HusbandryMountGenes.gene(wild, stat) <= 20);
        f.config.set("initial-genetic-max", 0); f.reload();
        HusbandryMountGenes.rollWild(wild, new Random(7));
        assertEquals(0, wild.genetics());
    }

    @Test void withoutRoomAFoalNeverBeatsTheBetterParentAndWithRoomItCan() {
        Random random = new Random(3);
        int best = 0, ceiling = 0;
        for (int i = 0; i < 2000; i++) {
            ceiling = Math.max(ceiling, HusbandryMountGenes.inheritStat(600, 600, 0, random));
            best = Math.max(best, HusbandryMountGenes.inheritStat(600, 600, 1, random));
        }
        assertTrue(ceiling <= 600, "No trust or feed means no climb");
        assertTrue(best > 600, "Trusting, well-fed parents can produce a better foal");
        assertTrue(best <= 600 + (1000 - 600) / 4 + 1);
        int floor = 1000;
        for (int i = 0; i < 2000; i++) floor = Math.min(floor, HusbandryMountGenes.inheritStat(600, 600, 1, random));
        assertTrue(floor < 600 && floor >= 600 - (1000 - 600) / 5.5 - 1);
    }

    @Test void veryDifferentParentsLowerTheCeiling() {
        Random random = new Random(5);
        int best = 0;
        for (int i = 0; i < 2000; i++) best = Math.max(best, HusbandryMountGenes.inheritStat(0, 1000, 1, random));
        assertTrue(best < 1000, "A foal of a weak and a perfect parent cannot reach the perfect one");
        assertEquals(0, HusbandryMountGenes.inheritStat(0, 0, 0, new Random(1)), "Clamped at zero");
    }

    @Test void foalsInheritEveryStatFromTheirOwnParentsGenes() throws Exception {
        HusbandryAnimal mother = animal(0, 200), father = animal(0, 200), foal = animal(0, 0);
        HusbandryMountGenes.set(mother, 1000, 0, 500);
        HusbandryMountGenes.set(father, 1000, 0, 500);
        HusbandryMountGenes.inherit(foal, mother, father, 1, new Random(11));
        assertTrue(HusbandryMountGenes.gene(foal, Stat.HEALTH) > 900);
        assertTrue(HusbandryMountGenes.gene(foal, Stat.SPEED) < 100);
        assertTrue(Math.abs(HusbandryMountGenes.gene(foal, Stat.JUMP) - 500) < 150);
        f.config.set("care-max", 0); f.reload();
        HusbandryMountGenes.inherit(foal, mother, father, 1, new Random(11));
        assertTrue(HusbandryMountGenes.gene(foal, Stat.HEALTH) <= 1000);
    }

    @Test void eachStatFollowsItsOwnGeneAndTrustAddsItsShare() {
        Horse horse = f.spawn(Horse.class); HusbandryAnimal animal = f.record(horse);
        HusbandryMountGenes.set(animal, 1000, 0, 500); animal.setCare(0);
        HusbandryMounts.applyStats(horse, animal);
        assertEquals(27, HusbandryMounts.maxHealth(horse), 0.001);
        assertEquals(0.12, HusbandryMounts.speed(horse), 0.001);
        assertEquals(0.65, HusbandryMounts.jump(horse), 0.001);
        animal.setCare(200);
        HusbandryMounts.applyStats(horse, animal);
        assertEquals(30, HusbandryMounts.maxHealth(horse), 0.001, "Full genes and full trust reach the max");
    }

    @Test void mountRecordsGetGenesAndKeepThemAcrossSavesUntilAStatsReset() {
        Horse spawned = f.spawn(Horse.class);
        HusbandryAnimal staff = HusbandrySpawner.createRecord(spawned, 700, 0);
        for (Stat stat : Stat.values()) assertEquals(700, HusbandryMountGenes.gene(staff, stat));
        HusbandryAnimal wild = HusbandrySpawner.createWildRecord(f.spawn(Horse.class));
        assertNotNull(wild.geneHealth()); assertTrue(wild.genetics() <= 20);

        HusbandryMountGenes.set(staff, 100, 200, 300); f.repository.upsertAnimal(staff);
        HusbandryAnimal read = f.repository.getAnimal(staff.uuid()).orElseThrow();
        assertEquals(100, read.geneHealth()); assertEquals(200, read.geneSpeed()); assertEquals(300, read.geneJump());

        f.repository.resetStaleStats("next-revision", new Random(1));
        HusbandryAnimal reset = f.repository.getAnimal(staff.uuid()).orElseThrow();
        assertNull(reset.geneHealth()); assertNull(reset.geneSpeed()); assertNull(reset.geneJump());
    }

    private static HusbandryAnimal animal(int genetics, int care) {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "HORSE", "Horse");
        animal.setGenetics(genetics);
        animal.setCare(care);
        return animal;
    }
}
