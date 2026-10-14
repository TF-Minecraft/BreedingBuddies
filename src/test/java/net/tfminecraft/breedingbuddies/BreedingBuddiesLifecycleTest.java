package net.tfminecraft.breedingbuddies;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.breedingbuddies.husbandry.HusbandryAnimalsCommand;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryConfig;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryIdentity;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryLifecycleListener;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryLoaderCoverageTest;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryLocator;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRepository;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryTickTask;
import net.tfminecraft.cooking.Cooking;

class BreedingBuddiesLifecycleTest {
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private ServerMock server;
    private BreedingBuddies previousPlugin;
    private Cooking previousCooking;
    private HusbandryRepository repository;
    private MockedStatic<HusbandryRepository> repositories;
    private MockedStatic<HusbandryLifecycleListener> lifecycle;
    private MockedStatic<HusbandryTickTask> ticks;
    private MockedStatic<HusbandryLocator> locator;
    private MockedStatic<HusbandryIdentity> identities;
    private HusbandryLoaderCoverageTest.ConfigSnapshot config;

    @BeforeEach void setUp() throws Exception {
        config = new HusbandryLoaderCoverageTest.ConfigSnapshot();
        server = MockBukkit.mock();
        previousPlugin = BreedingBuddies.plugin;
        previousCooking = Cooking.plugin;
        Cooking.plugin = null;
        repository = mock(HusbandryRepository.class);
        repositories = staticMock(HusbandryRepository.class);
        repositories.when(() -> HusbandryRepository.open(any(File.class))).thenReturn(repository);
        lifecycle = staticMock(HusbandryLifecycleListener.class);
        ticks = staticMock(HusbandryTickTask.class);
        locator = staticMock(HusbandryLocator.class);
        identities = staticMock(HusbandryIdentity.class);
    }

    @AfterEach void tearDown() throws Exception {
        config.close();
        MockBukkit.unmock();
        for (int i = statics.size() - 1; i >= 0; i--) statics.get(i).close();
        BreedingBuddies.plugin = previousPlugin;
        Cooking.plugin = previousCooking;
    }

    @Test void enablingWritesTheConfigOpensTheDatabaseAndRegistersCommandsListenersAndTasks() throws Exception {
        BreedingBuddies plugin = loadPlugin();

        assertSame(plugin, BreedingBuddies.plugin);
        assertSame(repository, plugin.getHusbandryRepository());
        assertTrue(new File(plugin.getDataFolder(), "Data").isDirectory());
        Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        assertFalse(Files.readString(config).isBlank());
        new YamlConfiguration().load(config.toFile());
        assertEquals(15, HusbandryConfig.maxAnimals());
        repositories.verify(() -> HusbandryRepository.open(new File(plugin.getDataFolder(), "Data/husbandry.db")));
        lifecycle.verify(HusbandryLifecycleListener::applyStatsRevision);
        identities.verify(() -> HusbandryIdentity.start(plugin, repository));
        assertInstanceOf(BreedingBuddiesCommand.class, plugin.getCommand("breedingbuddies").getExecutor());
        assertSame(plugin.getCommand("breedingbuddies").getExecutor(), plugin.getCommand("breedingbuddies").getTabCompleter());
        assertInstanceOf(HusbandryAnimalsCommand.class, plugin.getCommand("animals").getExecutor());
        assertSame(plugin.getCommand("animals").getExecutor(), plugin.getCommand("animals").getTabCompleter());
        assertTrue(plugin.getCommand("animals").getAliases().contains("livestock"));
        assertEquals(10, HandlerList.getRegisteredListeners(plugin).stream().map(listener -> listener.getListener().getClass()).distinct().count());
        ticks.verifyNoInteractions();

        server.getScheduler().performOneTick();

        lifecycle.verify(HusbandryLifecycleListener::resumeLoadedWorlds);
        ticks.verify(HusbandryTickTask::start);
        locator.verify(HusbandryLocator::scanUnloaded);

        server.getPluginManager().disablePlugin(plugin);

        ticks.verify(HusbandryTickTask::stop);
        lifecycle.verify(HusbandryLifecycleListener::flushLoadedForDisable);
        verify(repository).close();
        assertNull(plugin.getHusbandryRepository());
        plugin.onDisable();
        verify(repository, times(1)).close();
    }

    @Test void anExistingConfigIsKeptAndReloadRefreshesStatsAndTheTick() throws Exception {
        BreedingBuddies plugin = loadPlugin();
        Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        Files.writeString(config, "max-animals: 3\n");

        plugin.reloadAll();

        assertEquals(3, HusbandryConfig.maxAnimals());
        lifecycle.verify(HusbandryLifecycleListener::applyStatsRevision, times(2));
        ticks.verify(HusbandryTickTask::stop);
        ticks.verify(HusbandryTickTask::start);

        server.getPluginManager().disablePlugin(plugin);
        Files.writeString(config, "max-animals: 4\n");
        server.getPluginManager().enablePlugin(plugin);
        assertEquals("max-animals: 4\n", Files.readString(config));
        assertEquals(4, HusbandryConfig.maxAnimals());
    }

    @Test void cookingStillRunningLivestockKeepsBreedingBuddiesOff() {
        Cooking cooking = mock(Cooking.class);
        when(cooking.isEnabled()).thenReturn(true);
        when(cooking.isHusbandryEnabled()).thenReturn(true);
        Cooking.plugin = cooking;

        BreedingBuddies plugin = loadPlugin();

        assertFalse(plugin.isEnabled());
        assertNull(BreedingBuddies.plugin);
        repositories.verifyNoInteractions();
        identities.verifyNoInteractions();
        plugin.onDisable();
        ticks.verifyNoInteractions();
    }

    @Test void onlyAnEnabledCookingWithHusbandryOnBlocksStartup() {
        assertFalse(BreedingBuddies.cookingRunsLivestock());
        Cooking cooking = mock(Cooking.class);
        Cooking.plugin = cooking;
        assertFalse(BreedingBuddies.cookingRunsLivestock());
        when(cooking.isEnabled()).thenReturn(true);
        assertFalse(BreedingBuddies.cookingRunsLivestock());
        when(cooking.isHusbandryEnabled()).thenReturn(true);
        assertTrue(BreedingBuddies.cookingRunsLivestock());
    }

    @Test void databaseOpenFailureIsPropagatedInsteadOfEnablingAPartialPlugin() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        repositories.when(() -> HusbandryRepository.open(any(File.class))).thenThrow(failure);
        Throwable error = assertThrows(RuntimeException.class, this::loadPlugin);
        while (error.getCause() != null && error != failure) error = error.getCause();
        assertSame(failure, error);
        assertNull(BreedingBuddies.plugin.getHusbandryRepository());
        identities.verifyNoInteractions();
    }

    @Test void databaseCloseFailureStillClearsTheRepository() {
        BreedingBuddies plugin = loadPlugin();
        doThrow(new IllegalStateException("close failed")).when(repository).close();

        assertDoesNotThrow(() -> server.getPluginManager().disablePlugin(plugin));

        assertNull(plugin.getHusbandryRepository());
    }

    private BreedingBuddies loadPlugin() {
        MockBukkit.createMockPlugin("TLibs");
        MockBukkit.createMockPlugin("Cooking");
        return MockBukkit.load(BreedingBuddies.class);
    }

    private <T> MockedStatic<T> staticMock(Class<T> type) {
        MockedStatic<T> value = mockStatic(type); statics.add(value); return value;
    }
}
