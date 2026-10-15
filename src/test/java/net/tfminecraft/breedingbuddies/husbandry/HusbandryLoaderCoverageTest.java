package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

public class HusbandryLoaderCoverageTest {
    @TempDir Path directory;
    private ConfigSnapshot snapshot;
    @BeforeEach void setUp() throws Exception { snapshot = new ConfigSnapshot(); MockBukkit.mock(); }
    @AfterEach void tearDown() throws Exception { snapshot.close(); MockBukkit.unmock(); }

    @Test void completeYamlLoadsDurationsItemsSpeciesDropsBandsAndMounts() throws Exception {
        load("""
                max-animals: 3
                care-max: 120
                trust: {feed: 7, glove: 8, pet: 9, pet-cooldown: 2h}
                care:
                  down: {interval: 30s, amount: 0.75}
                decay-grace: 1h
                long-unload-force: 3h
                min-loaded: 5m
                affliction: {mean: 10h, min: 2h, max: 1h}
                milk-timer: 3m
                initial-genetic-max: 5
                max-genetics: 900
                min-roast-cuts: 2
                wool-timer: 4m
                grow-up: 5m
                shed-timer: 6m
                shed-chance: 0.25
                egg-timer: 7m
                items: {tame: tame, co-own: share, feed: feed, glove: glove, neuter: neuter, inspect: inspect, mount-stats: stats}
                remove-unowned: [' cow ', PIG, '', unknown-animal]
                damage: {other-players: false, owner: false, mobs: false, environment: false}
                breeding: {genetic-variance-multiplier: 0.3, genetic-slowdown-divisor: 0.7, care-influence: 0.04}
                stats-revision: '  new-revision  '
                restore-lost-animals: false
                profession: '  ranching  '
                exp: {min: 4, max: 9}
                species:
                  COW:
                    milk: true
                    grow-up: 2s
                    wool-timer: 0s
                    milk-timer: 2m
                    exp: {min: 5, max: 7}
                    slaughter:
                      meat: roast-beef
                      drops:
                        mode: counted
                        common:
                          - {path: ' leather ', amount: '2', weight: '3'}
                          - {amount: -1}
                          - {path: fallback, amount: bad, weight: bad}
                          - {path: skipped, weight: 0}
                        rare: [{path: rare, weight: 2}]
                        epic: [{path: epic, amount: 3}]
                        legendary: [{path: legendary, weight: 4}]
                    shear: {}
                    shed:
                      drops: {common: [{path: hair, amount: 2}]}
                    egg: vanilla
                  WOLF: {}
                  invalid-species: {}
                quality-from-genetics:
                  - {min: '200', stars: 9}
                  - {min: 0, stars: -1}
                  - {min: 100, stars: '3'}
                  - {min: bad, stars: bad}
                amount-from-genetics:
                  - {min: '100', roast-cuts: 4, wool: 3, secondary-extra: 9}
                  - {min: 0, roast-cuts: 0, wool: 0, secondary-extra: -1}
                mounts:
                  speed: {min-pct: 0.1, genetics-pct: 0.2, care-pct: 0.3}
                  nerf: true
                  nerf-divisor: 2
                  HORSE: {min-health: 8, max-health: 32, min-speed: 0.2, max-speed: 0.7, min-jump: 0.4, max-jump: 0.9}
                  DONKEY: {}
                  COW: invalid-scalar
                  invalid-mount: {}
                """);
        assertEquals(3, HusbandryConfig.maxAnimals()); assertEquals(120, HusbandryConfig.careMax());
        assertEquals(7, HusbandryConfig.trustFor(HusbandryTrust.Gesture.FEED)); assertEquals(8, HusbandryConfig.trustFor(HusbandryTrust.Gesture.GLOVE)); assertEquals(9, HusbandryConfig.trustFor(HusbandryTrust.Gesture.PET)); assertEquals(7200, HusbandryConfig.petCooldownSeconds());
        assertEquals(30, HusbandryConfig.careDownIntervalSeconds()); assertEquals(0.75, HusbandryConfig.careDownAmount());
        assertEquals(3600, HusbandryConfig.decayGraceSeconds()); assertEquals(10800, HusbandryConfig.longUnloadForceSeconds()); assertEquals(300, HusbandryConfig.minLoadedSeconds());
        assertEquals(10, HusbandryConfig.afflictionMeanHours()); assertEquals(2, HusbandryConfig.afflictionMinHours()); assertEquals(2, HusbandryConfig.afflictionMaxHours());
        assertEquals(180, HusbandryConfig.milkCooldownSeconds()); assertEquals(5, HusbandryConfig.initialGeneticMax()); assertEquals(900, HusbandryConfig.maxGenetics()); assertEquals(2, HusbandryConfig.minRoastCuts());
        assertEquals(180, HusbandryConfig.milkTimerSeconds());
        assertEquals(240, HusbandryConfig.woolTimerSeconds()); assertEquals(300, HusbandryConfig.growUpSeconds()); assertEquals(360, HusbandryConfig.shedTimerSeconds()); assertEquals(0.25, HusbandryConfig.shedChance()); assertEquals(420, HusbandryConfig.eggTimerSeconds());
        assertEquals(List.of("tame", "share", "feed", "glove", "neuter", "inspect", "stats"), List.of(HusbandryConfig.tameItem(), HusbandryConfig.coOwnItem(), HusbandryConfig.feedItem(), HusbandryConfig.gloveItem(), HusbandryConfig.neuterItem(), HusbandryConfig.inspectItem(), HusbandryConfig.mountStatsItem()));
        assertEquals(Set.of(EntityType.COW, EntityType.PIG), HusbandryConfig.removeUnowned()); assertTrue(HusbandryConfig.isRemoveUnowned(EntityType.COW)); assertFalse(HusbandryConfig.isRemoveUnowned(null)); assertFalse(HusbandryConfig.isRemoveUnowned(EntityType.WOLF));
        assertFalse(HusbandryConfig.damageOtherPlayers()); assertFalse(HusbandryConfig.damageOwner()); assertFalse(HusbandryConfig.damageMobs()); assertFalse(HusbandryConfig.damageEnvironment());
        assertEquals(0.3, HusbandryConfig.geneticVarianceMultiplier()); assertEquals(0.7, HusbandryConfig.geneticSlowdownDivisor()); assertEquals(0.04, HusbandryConfig.careInfluence());
        assertEquals("new-revision", HusbandryConfig.statsRevision()); assertFalse(HusbandryConfig.restoreLostAnimals()); assertEquals("ranching", HusbandryConfig.professionId());
        assertEquals(0.1, HusbandryConfig.mountMinPct()); assertEquals(0.2, HusbandryConfig.mountGeneticsPct()); assertEquals(0.3, HusbandryConfig.mountCarePct());
        assertEquals(Set.of(EntityType.COW, EntityType.WOLF), HusbandryConfig.species().keySet());
        HusbandrySpecies cow = HusbandryConfig.species(EntityType.COW);
        assertTrue(cow.canMilk()); assertTrue(cow.canSlaughter()); assertFalse(cow.canShear()); assertTrue(cow.canShed()); assertTrue(cow.hasEgg()); assertTrue(cow.vanillaEggs()); assertEquals("roast-beef", cow.slaughterMeat());
        assertEquals(2, HusbandryConfig.growUpSeconds(EntityType.COW)); assertEquals(1, HusbandryConfig.woolTimerSeconds(EntityType.COW)); assertEquals(120, HusbandryConfig.milkTimerSeconds(EntityType.COW));
        for (EntityType fallback : Arrays.asList(null, EntityType.PIG, EntityType.WOLF)) {
            assertEquals(300, HusbandryConfig.growUpSeconds(fallback)); assertEquals(240, HusbandryConfig.woolTimerSeconds(fallback)); assertEquals(180, HusbandryConfig.milkTimerSeconds(fallback));
            assertEquals(4, HusbandryConfig.expFor(fallback).min()); assertEquals(9, HusbandryConfig.expFor(fallback).max());
        }
        assertEquals(5, HusbandryConfig.expFor(EntityType.COW).min()); assertEquals(7, HusbandryConfig.expFor(EntityType.COW).max());
        HusbandryDropTable drops = cow.slaughterDrops(); assertTrue(drops.counted()); assertEquals(3, drops.common().size());
        assertEquals("leather", drops.common().getFirst().path()); assertEquals(2, drops.common().getFirst().amount()); assertEquals(3, drops.common().getFirst().weight());
        assertEquals("", drops.common().get(1).path()); assertEquals(1, drops.common().get(1).amount()); assertEquals(1, drops.common().get(2).weight());
        assertEquals("rare", drops.rare().getFirst().path()); assertEquals(3, drops.epic().getFirst().amount()); assertEquals(4, drops.legendary().getFirst().weight()); assertEquals("hair", cow.shedDrops().common().getFirst().path());
        HusbandryMountStats horse = HusbandryConfig.mountStats(EntityType.HORSE); assertEquals(EntityType.HORSE, horse.type());
        assertEquals(8, horse.minHealth()); assertEquals(32, horse.maxHealth()); assertEquals(0.2, horse.minSpeed()); assertEquals(0.7, horse.maxSpeed()); assertEquals(0.4, horse.minJump()); assertEquals(0.9, horse.maxJump());
        assertEquals(Set.of(EntityType.HORSE, EntityType.DONKEY), HusbandryConfig.mounts().keySet()); assertEquals(15, HusbandryConfig.mountStats(EntityType.DONKEY).minHealth());
        assertEquals(List.of(0, 0, 100, 200), HusbandryConfig.qualityBands().stream().map(HusbandryQualityBand::minGenetics).toList()); assertEquals(1, HusbandryConfig.starsForGenetics(0)); assertEquals(5, HusbandryConfig.starsForGenetics(200));
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Daisy"); animal.setGenetics(200); animal.setCare(60);
        assertEquals(100, HusbandryConfig.effectiveGenetics(animal)); assertEquals(new HusbandryQualityRange.Bounds(3, 5), HusbandryConfig.qualityRange(animal));
        assertEquals(new HusbandryQualityRange.Bounds(1, 1), HusbandryConfig.qualityRange(null)); assertEquals(0, HusbandryConfig.effectiveGenetics(null));
        assertEquals(2, HusbandryConfig.roastCutsFor(-1)); assertEquals(4, HusbandryConfig.roastCutsFor(100)); assertEquals(1, HusbandryConfig.woolFor(0)); assertEquals(3, HusbandryConfig.woolFor(100)); assertEquals(0, HusbandryConfig.secondaryExtraFor(0)); assertEquals(3, HusbandryConfig.hideCount(100)); assertEquals(0, HusbandryConfig.secondaryExtraFor(100, null));
        assertTrue(HusbandryConfig.isHusbandryType(EntityType.WOLF)); assertTrue(HusbandryConfig.isHusbandryType(EntityType.PIG)); assertFalse(HusbandryConfig.isHusbandryType(EntityType.ZOMBIE)); assertFalse(HusbandryConfig.isHusbandryType(null));
        assertThrows(UnsupportedOperationException.class, () -> HusbandryConfig.species().clear()); assertThrows(UnsupportedOperationException.class, () -> HusbandryConfig.qualityBands().clear());
    }

    @Test void mountSharesApplyToEveryStatAndTheOldSpeedNameStillLoads() throws Exception {
        load("mounts:\n  shares: {min-pct: 0.2, genetics-pct: 0.6, care-pct: 0.2}\n  speed: {min-pct: 0.9}\n");
        assertEquals(0.2, HusbandryConfig.mountMinPct()); assertEquals(0.6, HusbandryConfig.mountGeneticsPct()); assertEquals(0.2, HusbandryConfig.mountCarePct());
        assertTrue(HusbandryConfig.mounts().isEmpty(), "Neither section is a mount type");
        load("mounts:\n  speed: {min-pct: 0.3}\n");
        assertEquals(0.3, HusbandryConfig.mountMinPct()); assertEquals(0.5, HusbandryConfig.mountGeneticsPct()); assertEquals(0.1, HusbandryConfig.mountCarePct());
    }

    @Test void legacyDurationsAndEmptySectionsRemainSupported() throws Exception {
        load("""
                care-down-per-hour: 3
                decay-grace-hours: 2.5
                long-unload-force-hours: 4
                min-loaded-seconds: 5
                affliction-mean-hours: 7
                affliction-min-hours: 5
                affliction-max-hours: 9
                milk-cooldown-minutes: 6
                wool-timer-hours: 7
                """);
        assertEquals(3600, HusbandryConfig.careDownIntervalSeconds()); assertEquals(3, HusbandryConfig.careDownAmount());
        assertEquals(9000, HusbandryConfig.decayGraceSeconds()); assertEquals(14400, HusbandryConfig.longUnloadForceSeconds()); assertEquals(5, HusbandryConfig.minLoadedSeconds());
        assertEquals(7, HusbandryConfig.afflictionMeanHours()); assertEquals(5, HusbandryConfig.afflictionMinHours()); assertEquals(9, HusbandryConfig.afflictionMaxHours()); assertEquals(360, HusbandryConfig.milkCooldownSeconds()); assertEquals(25200, HusbandryConfig.woolTimerSeconds());
        assertTrue(HusbandryConfig.species().isEmpty()); assertTrue(HusbandryConfig.mounts().isEmpty()); assertTrue(HusbandryConfig.qualityBands().isEmpty());
        load("milk-cooldown: 2m\n"); assertEquals(120, HusbandryConfig.milkCooldownSeconds());
        load(""); assertEquals(1200, HusbandryConfig.milkCooldownSeconds()); assertEquals(86400, HusbandryConfig.decayGraceSeconds()); assertEquals(6, HusbandryConfig.expFor(null).min());
        assertEquals(10, HusbandryConfig.trustFor(HusbandryTrust.Gesture.FEED)); assertEquals(5, HusbandryConfig.trustFor(HusbandryTrust.Gesture.PET)); assertEquals(72000, HusbandryConfig.petCooldownSeconds());
    }

    @Test void missingAndMalformedFilesResetToTheSameDefaultsAsEmptyYaml() throws Exception {
        load(""); int defaultWool = HusbandryConfig.woolTimerSeconds();
        load("max-animals: 99\nwool-timer: 1s\nprofession: previous\n");
        new HusbandryLoader().load(directory.resolve("absent.yml").toFile());
        assertEquals(15, HusbandryConfig.maxAnimals()); assertEquals(defaultWool, HusbandryConfig.woolTimerSeconds(), "Read failures must not silently change the default wool interval"); assertEquals("farming", HusbandryConfig.professionId());
        load("max-animals: [broken\n"); assertEquals(15, HusbandryConfig.maxAnimals()); assertTrue(HusbandryConfig.species().isEmpty()); assertEquals("1", HusbandryConfig.statsRevision()); assertTrue(HusbandryConfig.restoreLostAnimals());
    }

    @Test void publicConfigApiClampsBoundsAndDefensivelyCopiesCallerCollections() {
        Set<EntityType> removed = new HashSet<>(Set.of(EntityType.COW));
        Map<EntityType, HusbandrySpecies> species = new HashMap<>();
        List<HusbandryQualityBand> quality = new ArrayList<>(List.of(new HusbandryQualityBand(0, 2)));
        List<HusbandryAmountBand> amounts = new ArrayList<>(List.of(new HusbandryAmountBand(0, 3, 4)));
        HusbandryConfig.apply(-1, 0, -1, 3, -1, -1, -1, 6, 4, 2, -1, -1, 0, -1, -1, -1, -1, 2, -1,
                null, null, null, null, null, null, null, removed, species, true, true, true, true, quality, Map.of(), 0.4, 0.6, amounts);
        removed.clear(); species.put(EntityType.PIG, species(EntityType.PIG)); quality.clear(); amounts.clear();
        assertEquals(1, HusbandryConfig.maxAnimals()); assertEquals(1, HusbandryConfig.careMax()); assertEquals(1, HusbandryConfig.careDownIntervalSeconds()); assertEquals(4, HusbandryConfig.afflictionMaxHours()); assertEquals(1, HusbandryConfig.maxGenetics());
        assertEquals(0, HusbandryConfig.decayGraceSeconds()); assertEquals(0, HusbandryConfig.longUnloadForceSeconds()); assertEquals(0, HusbandryConfig.minLoadedSeconds()); assertEquals(0, HusbandryConfig.milkCooldownSeconds()); assertEquals(0, HusbandryConfig.initialGeneticMax()); assertEquals(0, HusbandryConfig.minRoastCuts()); assertEquals(0, HusbandryConfig.woolTimerSeconds()); assertEquals(0, HusbandryConfig.growUpSeconds()); assertEquals(0, HusbandryConfig.shedTimerSeconds()); assertEquals(0, HusbandryConfig.eggTimerSeconds()); assertEquals(1, HusbandryConfig.shedChance());
        assertEquals(List.of("", "", "", "", "", "", ""), List.of(HusbandryConfig.tameItem(), HusbandryConfig.coOwnItem(), HusbandryConfig.feedItem(), HusbandryConfig.gloveItem(), HusbandryConfig.neuterItem(), HusbandryConfig.inspectItem(), HusbandryConfig.mountStatsItem()));
        assertEquals(Set.of(EntityType.COW), HusbandryConfig.removeUnowned()); assertTrue(HusbandryConfig.species().isEmpty()); assertEquals(2, HusbandryConfig.starsForGenetics(0)); assertEquals(3, HusbandryConfig.roastCutsFor(0)); assertEquals(4, HusbandryConfig.woolFor(0));
        HusbandryConfig.setTrust(-1, -2, -3, -4); assertEquals(0, HusbandryConfig.trustFor(HusbandryTrust.Gesture.FEED)); assertEquals(0, HusbandryConfig.trustFor(HusbandryTrust.Gesture.GLOVE)); assertEquals(0, HusbandryConfig.trustFor(HusbandryTrust.Gesture.PET)); assertEquals(0, HusbandryConfig.petCooldownSeconds());
        HusbandryConfig.setMountShares(-1, -2, -3); assertEquals(0, HusbandryConfig.mountMinPct()); assertEquals(0, HusbandryConfig.mountGeneticsPct()); assertEquals(0, HusbandryConfig.mountCarePct());
        HusbandryConfig.setBreeding(-1, 0.2, -3); assertEquals(0, HusbandryConfig.geneticVarianceMultiplier()); assertEquals(0.2, HusbandryConfig.geneticSlowdownDivisor()); assertEquals(0, HusbandryConfig.careInfluence());
        HusbandryConfig.setStatsRevision(null); HusbandryConfig.setProfessionExp(null, null); assertEquals("", HusbandryConfig.statsRevision()); assertEquals("", HusbandryConfig.professionId()); assertEquals(6, HusbandryConfig.expFor(null).min());
        assertEquals(0, HusbandryConfig.resolveMilkTimerSeconds(0, -1)); assertEquals(2, HusbandryConfig.resolveMilkTimerSeconds(2, -1)); assertEquals(0, HusbandryConfig.resolveWoolTimerSeconds(0, -1)); assertEquals(2, HusbandryConfig.resolveWoolTimerSeconds(2, -1));
    }

    @Test void durationHelpersPreserveModernPrecedenceAndFractionalLegacyHours() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(5, HusbandryDuration.parseHours(config, "new", "old", 5));
        config.set("old", 2.5); assertEquals(2.5, HusbandryDuration.parseHours(config, "new", "old", 5));
        config.set("new", "30m"); assertEquals(0.5, HusbandryDuration.parseHours(config, "new", "old", 5));
        MemoryConfiguration section = new MemoryConfiguration();
        assertEquals(7, HusbandryDuration.parseHours(section, "new", "old", 7)); assertEquals(7, HusbandryDuration.parseHours((org.bukkit.configuration.ConfigurationSection) null, "new", null, 7));
        section.set("old", 3.5); assertEquals(3.5, HusbandryDuration.parseHours(section, "new", "old", 7));
        section.set("new", "15m"); assertEquals(0.25, HusbandryDuration.parseHours(section, "new", "old", 7));
        assertEquals(3600, HusbandryDuration.parseCareIntervalSeconds(config, "absent", "old", 10));
        assertEquals(2.5, HusbandryDuration.parseCareAmount(config, "absent", "old", 10));
    }

    private void load(String yaml) throws Exception { Path file = directory.resolve("husbandry.yml"); Files.writeString(file, yaml); new HusbandryLoader().load(file.toFile()); }
    static HusbandrySpecies species(EntityType type) { return new HusbandrySpecies(type, false, "", null, null, null, "", 0, 0, 0); }

    /** Restores original static configuration only; test inputs always use the public YAML/API. */
    public static final class ConfigSnapshot implements AutoCloseable {
        private final Map<Field, Object> values = new LinkedHashMap<>();
        public ConfigSnapshot() throws IllegalAccessException {
            for (Field field : HusbandryConfig.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    field.setAccessible(true); values.put(field, field.get(null));
                }
            }
        }
        @Override public void close() throws IllegalAccessException { for (var entry : values.entrySet()) entry.getKey().set(null, entry.getValue()); }
    }
}
