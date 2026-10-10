package net.tfminecraft.breedingbuddies;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.tfminecraft.breedingbuddies.listeners.DayChangeListener;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Starts a MockBukkit server with the plugin enabled and its default configuration.
 * MMOItems and MythicLib are present as stub plugins; MythicLib's NBT lookup is
 * faked so that items made with {@link #mmoItem(String)} report an MMOItems ID.
 */
public abstract class PluginTestBase {
    protected static final Path DATA_DIR = Path.of("plugins", "BreedingBuddies", "Data");
    private static final NamespacedKey TEST_MMOITEM_ID = new NamespacedKey("bbtest", "mmoitems_id");

    protected ServerMock server;
    protected BreedingBuddies plugin;
    protected WorldMock world;
    private MockedStatic<NBTItem> nbtItems;

    @BeforeEach
    void startServer() throws IOException {
        deleteRecursively(Path.of("plugins"));
        PluginData.getUnownedAnimals().clear();
        PluginData.getPlayerAnimals().clear();
        PluginData.getChunkAreas().clear();
        nbtItems = mockStatic(NBTItem.class);
        nbtItems.when(() -> NBTItem.get(any(ItemStack.class))).thenAnswer(call -> fakeNbt(call.getArgument(0)));
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("MMOItems");
        MockBukkit.createMockPlugin("MythicLib");
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(BreedingBuddies.class);
    }

    @AfterEach
    void stopServer() throws Exception {
        try {
            awaitDayChange();
        } finally {
            try {
                MockBukkit.unmock();
            } finally {
                nbtItems.close();
                MMOItems.plugin = null;
                setStatic(DayChangeListener.class, "isChangingDay", false);
                setStatic(BreedingBuddies.class, "instance", null);
            }
        }
    }

    private static NBTItem fakeNbt(ItemStack item) {
        NBTItem nbt = mock(NBTItem.class);
        String id = null;
        if (item != null && item.hasItemMeta()) {
            id = item.getItemMeta().getPersistentDataContainer().get(TEST_MMOITEM_ID, PersistentDataType.STRING);
        }
        when(nbt.hasType()).thenReturn(id != null);
        when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn(id);
        return nbt;
    }

    /** An item that the faked MythicLib NBT lookup reports as the given MMOItems ID. */
    protected static ItemStack mmoItem(String id) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(TEST_MMOITEM_ID, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Installs an MMOItems stub that builds a copy of each registered item; unknown
     * IDs build nothing, as MMOItems does. Keys are {@code TYPE:ID}.
     */
    protected static MMOItems stubMmoItems(Map<String, ItemStack> items) {
        MMOItems mmoItems = mock(MMOItems.class, RETURNS_DEEP_STUBS);
        Map<String, Type> types = new HashMap<>();
        when(mmoItems.getTypes().get(anyString())).thenAnswer(call -> types.computeIfAbsent(call.getArgument(0), name -> {
            Type type = mock(Type.class);
            when(type.getId()).thenReturn(name);
            return type;
        }));
        when(mmoItems.getItem(any(Type.class), anyString())).thenAnswer(call -> {
            ItemStack item = items.get(((Type) call.getArgument(0)).getId() + ":" + call.getArgument(1));
            return item == null ? null : item.clone();
        });
        MMOItems.plugin = mmoItems;
        return mmoItems;
    }

    protected Location spawnPoint() {
        return new Location(world, 8, 64, 8);
    }

    protected <T extends Entity> T spawn(Class<T> type) {
        return world.spawn(spawnPoint(), type);
    }

    protected PlayerMock player() {
        PlayerMock player = server.addPlayer();
        player.teleport(spawnPoint());
        return player;
    }

    protected static FarmAnimal own(PlayerMock owner, Entity entity) {
        FarmAnimal animal = new FarmAnimal(entity.getUniqueId(), "Daisy", owner.getUniqueId(), entity);
        OwnershipManager.registerOwnership(owner.getUniqueId(), animal);
        return animal;
    }

    /** Points the stored last day change at the given number of days ago. */
    protected void setLastDayChange(long daysAgo) {
        YamlConfiguration numbers = YamlConfiguration.loadConfiguration(plugin.getNumbersFile());
        numbers.set("lastDayChangeDate", Instant.now().getEpochSecond() - daysAgo * 86_400 - 60);
        DayChangeListener.setNumbersConfig(numbers);
    }

    /**
     * A chunk at the given coordinates holding the given entities. MockBukkit chunks
     * contain no blocks, so this stands in when an area needs water.
     */
    protected static Chunk fakeChunk(int x, int z, boolean water, Entity... entities) {
        Chunk chunk = mock(Chunk.class);
        boolean[] forced = {false};
        doAnswer(call -> forced[0] = call.getArgument(0)).when(chunk).setForceLoaded(anyBoolean());
        when(chunk.isForceLoaded()).thenAnswer(call -> forced[0]);
        when(chunk.getX()).thenReturn(x);
        when(chunk.getZ()).thenReturn(z);
        when(chunk.contains(any(BlockData.class))).thenReturn(water);
        when(chunk.getEntities()).thenReturn(entities);
        return chunk;
    }

    protected static ChunkArea stable(Chunk... chunks) {
        ChunkArea area = new ChunkArea(new HashSet<>(Arrays.asList(chunks)));
        PluginData.getChunkAreas().add(area);
        return area;
    }

    /**
     * Waits for a background day change to finish. MockBukkit's own wait can return
     * before a just-submitted task starts; the listener clears its flag when done.
     */
    protected void awaitDayChange() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (DayChangeListener.isIsChangingDay()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("day change did not finish");
            }
            Thread.sleep(5);
        }
        server.getScheduler().waitAsyncTasksFinished();
    }

    protected static void setStatic(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    protected static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    protected static Path playerFile(UUID player) {
        return DATA_DIR.resolve("PlayerData").resolve(player + ".json");
    }

    /** Collects log records written to a logger while a test runs. */
    protected static final class LogCapture extends Handler implements AutoCloseable {
        private final Logger logger;
        public final List<LogRecord> records = new ArrayList<>();

        public LogCapture(Logger logger) {
            this.logger = logger;
            logger.addHandler(this);
        }

        public boolean contains(String text) {
            return records.stream().anyMatch(record -> record.getMessage().contains(text));
        }

        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }
    }
}
