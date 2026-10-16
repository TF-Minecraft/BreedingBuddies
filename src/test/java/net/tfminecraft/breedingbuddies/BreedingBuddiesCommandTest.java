package net.tfminecraft.breedingbuddies;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.breedingbuddies.husbandry.HusbandryAnimal;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryConfig;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryEntities;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRepository;
import net.tfminecraft.breedingbuddies.husbandry.HusbandrySpawner;
import net.tfminecraft.breedingbuddies.husbandry.HusbandrySpecies;

class BreedingBuddiesCommandTest {
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final BreedingBuddiesCommand commands = new BreedingBuddiesCommand();
    private final Command command = mock(Command.class);
    private ServerMock server;
    private PlayerMock player;
    private BreedingBuddies plugin;
    private BreedingBuddies previousPlugin;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Admin");
        player.setOp(true);
        previousPlugin = BreedingBuddies.plugin;
        plugin = mock(BreedingBuddies.class);
        BreedingBuddies.plugin = plugin;
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
        for (int i = statics.size() - 1; i >= 0; i--) statics.get(i).close();
        BreedingBuddies.plugin = previousPlugin;
    }

    @Test void staffOnlyCommandsListTheirUsageAndReload() {
        player.setOp(false);
        run("reload");
        assertEquals(List.of("§cNo permission."), messages());
        assertTrue(tabs("").isEmpty());
        player.setOp(true);
        run();
        assertEquals(3, messages().size());
        run("unknown");
        assertEquals(3, messages().size());
        run("reload");
        verify(plugin).reloadAll();
        assertEquals(List.of("§aBreedingBuddies config reloaded."), messages());
        BreedingBuddies.plugin = null;
        run("reload");
        assertEquals(List.of("§cBreedingBuddies is not running."), messages());
    }

    @Test void spawnRejectsConsolesAndMalformedArguments() {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(BreedingBuddiesCommand.PERMISSION)).thenReturn(true);
        run(console, "spawn", "cow");
        verify(console).sendMessage("§cOnly players can spawn animals.");
        run("spawn");
        assertTrue(messages().getFirst().contains("Usage:"));
        run("spawn", "not-an-entity");
        assertEquals(List.of("§cUnknown entity type."), messages());
        MockedStatic<HusbandryConfig> config = staticMock(HusbandryConfig.class);
        run("spawn", "COW");
        assertEquals(List.of("§cThat type is not a husbandry species."), messages());
        config.when(() -> HusbandryConfig.species(EntityType.COW)).thenReturn(mock(HusbandrySpecies.class));
        run("spawn", "cow", "bad");
        assertEquals(List.of("§cGenetics must be a number."), messages());
        run("spawn", "cow", "2", "bad");
        assertEquals(List.of("§cCare must be a number."), messages());
    }

    @Test void spawnUsesDefaultsAndReportsClampedGeneticsAndCare() {
        MockedStatic<HusbandryConfig> config = staticMock(HusbandryConfig.class);
        config.when(() -> HusbandryConfig.species(EntityType.COW)).thenReturn(mock(HusbandrySpecies.class));
        config.when(HusbandryConfig::initialGeneticMax).thenReturn(20);
        config.when(HusbandryConfig::maxGenetics).thenReturn(1000);
        config.when(HusbandryConfig::careMax).thenReturn(200);
        MockedStatic<HusbandrySpawner> spawner = staticMock(HusbandrySpawner.class);
        run("spawn", "cow");
        spawner.verify(() -> HusbandrySpawner.spawn(player, EntityType.COW, 20, 0));
        assertEquals(List.of("§cFailed to spawn animal."), messages());
        Cow cow = mock(Cow.class);
        when(cow.getName()).thenReturn("Daisy");
        spawner.when(() -> HusbandrySpawner.spawn(player, EntityType.COW, 2000, -2)).thenReturn(cow);
        run("spawn", "cow", "2000", "-2");
        assertEquals(List.of("§aSpawned Daisy genetics 1000 care 0."), messages());
    }

    @Test void saveRequiresTheDatabaseAndFlushesOnlyLoadedAnimals() {
        BreedingBuddies.plugin = null;
        run("save");
        assertEquals(List.of("§cThe animal database is not open."), messages());
        BreedingBuddies.plugin = plugin;
        run("save");
        assertEquals(List.of("§cThe animal database is not open."), messages());
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(plugin.getHusbandryRepository()).thenReturn(repository);
        MockedStatic<HusbandryEntities> entities = staticMock(HusbandryEntities.class);
        entities.when(HusbandryEntities::snapshotLoaded).thenReturn(List.of());
        run("save");
        verify(repository, never()).upsertAnimals(anyCollection());
        assertEquals(List.of("§aSaved 0 loaded animals."), messages());
        List<HusbandryAnimal> loaded = List.of(mock(HusbandryAnimal.class));
        entities.when(HusbandryEntities::snapshotLoaded).thenReturn(loaded);
        run("save");
        verify(repository).upsertAnimals(loaded);
        verify(repository, times(2)).checkpointWal(false);
        assertEquals(List.of("§aSaved 1 loaded animals."), messages());
    }

    @Test void completionsOfferSubcommandsSpeciesAndStatHints() {
        assertEquals(List.of("reload", "spawn", "save"), tabs(""));
        assertEquals(List.of("spawn", "save"), tabs("s"));
        MockedStatic<HusbandryConfig> config = staticMock(HusbandryConfig.class);
        config.when(HusbandryConfig::species).thenReturn(Map.of(EntityType.PIG, mock(HusbandrySpecies.class),
                EntityType.COW, mock(HusbandrySpecies.class), EntityType.CHICKEN, mock(HusbandrySpecies.class)));
        assertEquals(List.of("CHICKEN", "COW"), tabs("spawn", "c"));
        assertEquals(List.of("0", "20", "1000"), tabs("spawn", "cow", ""));
        assertEquals(List.of("0", "100", "200"), tabs("spawn", "cow", "1", ""));
        assertEquals(List.of("20"), tabs("spawn", "cow", "2"));
        assertEquals(List.of("100"), tabs("spawn", "cow", "1", "1"));
        assertTrue(tabs("spawn", "cow", "9").isEmpty());
        assertTrue(tabs("spawn", "cow", "1", "9").isEmpty());
        assertTrue(tabs("spawn", "cow", "1", "2", "").isEmpty());
        assertTrue(tabs("save", "").isEmpty());
    }

    private void run(String... args) { run(player, args); }
    private void run(CommandSender sender, String... args) { assertTrue(commands.onCommand(sender, command, "breedingbuddies", args)); }
    private List<String> tabs(String... args) { return commands.onTabComplete(player, command, "breedingbuddies", args); }
    private List<String> messages() {
        List<String> out = new ArrayList<>(); String value;
        while ((value = player.nextMessage()) != null) out.add(value);
        return out;
    }
    private <T> MockedStatic<T> staticMock(Class<T> type) {
        MockedStatic<T> value = mockStatic(type); statics.add(value); return value;
    }
}
