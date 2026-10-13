package net.tfminecraft.breedingbuddies;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.breedingbuddies.listeners.DayChangeListener;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;



public class PluginData {

	private static Map<UUID, FarmAnimal> unownedAnimals = new HashMap<>();
	private static Map<UUID, Set<FarmAnimal>> playerAnimals = new HashMap<>();
	private static ArrayList<ChunkArea> chunkAreas = new ArrayList<ChunkArea>();

	private static final String PLAYER_DATA_DIR = "plugins/BreedingBuddies/Data/PlayerData";
	private static final String UNOWNED_ANIMALS_DATA_DIR = "plugins/BreedingBuddies/Data/unownedanimals.json";
	private static final String STABLE_CHUNKS_DATA_DIR = "plugins/BreedingBuddies/Data/stablechunks.json";

	private PluginData() {
	}

	public static void saveAllData(boolean force) {
		if (!force && DayChangeListener.isIsChangingDay()) return;
		PluginData.saveAnimals();
		PluginData.saveStableChunks();
		Bukkit.getLogger().info("[BreedingBuddies] Saving all data...");
	}

	public static void loadAllData() {
		loadStableChunks();
		loadAnimals();
		Bukkit.getLogger().info("[BreedingBuddies] Loading all data...");
	}

	public static void saveStableChunks() {
		try {
			File file = new File(STABLE_CHUNKS_DATA_DIR);
			file.getParentFile().mkdirs(); // Ensure the directory exists

			JSONArray chunkAreasJsonArray = new JSONArray();
			for (ChunkArea chunkArea : chunkAreas) {
				JSONObject chunkAreaJson = new JSONObject();
				chunkAreaJson.put("daysAbandoned", chunkArea.getDaysAbandoned());

				JSONArray chunksJsonArray = new JSONArray();
				for (Chunk chunk : chunkArea.getChunks()) {
					JSONObject chunkJson = new JSONObject();
					chunkJson.put("world", chunk.getWorld().getName());
					chunkJson.put("x", chunk.getX());
					chunkJson.put("z", chunk.getZ());
					chunksJsonArray.add(chunkJson);
				}
				chunkAreaJson.put("chunks", chunksJsonArray);
				chunkAreasJsonArray.add(chunkAreaJson);
			}

			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(chunkAreasJsonArray.toJSONString());
			fileWriter.flush();
			fileWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void loadStableChunks() {
		try {
			File file = new File(STABLE_CHUNKS_DATA_DIR);
			if (!file.exists()) {
				return;
			}

			JSONParser parser = new JSONParser();
			FileReader reader = new FileReader(file);
			JSONArray chunkAreasJsonArray = (JSONArray) parser.parse(reader);
			reader.close();

			chunkAreas.clear();

			for (Object chunkAreaObj : chunkAreasJsonArray) {
				JSONObject chunkAreaJson = (JSONObject) chunkAreaObj;
				int daysAbandoned = ((Long) chunkAreaJson.get("daysAbandoned")).intValue();

				JSONArray chunksJsonArray = (JSONArray) chunkAreaJson.get("chunks");
				Set<Chunk> chunks = new HashSet<>();
				for (Object chunkObj : chunksJsonArray) {
					JSONObject chunkJson = (JSONObject) chunkObj;
					String worldName = (String) chunkJson.get("world");
					World world = Bukkit.getWorld(worldName);
					if (world != null) {
						int x = ((Long) chunkJson.get("x")).intValue();
						int z = ((Long) chunkJson.get("z")).intValue();
						Chunk chunk = world.getChunkAt(x, z);
						chunks.add(chunk);
					} else {
						System.err.println("World " + worldName + " not found.");
					}
				}

				ChunkArea chunkArea = new ChunkArea(chunks);
				chunkArea.setDaysAbandoned(daysAbandoned);
				chunkAreas.add(chunkArea);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    private static AnimalOwnershipStore ownership;
    static AnimalOwnershipStore ownership() { return ownership; }
    public static boolean ownershipReady() { return ownership != null && ownership.ready; }
    public static boolean isHeld(UUID id) { return ownership == null || ownership.held(id); }
    public static void saveUnownedAnimals() { saveAnimals(); }
    public static void loadUnownedAnimals() { loadAnimals(); }
    static void saveAllPlayerData() { saveAnimals(); }
    public static void savePlayerData(UUID account, Set<FarmAnimal> ignored) { saveAnimals(); }
    public static void loadPlayerData(UUID account, Map<UUID, FarmAnimal> ignored) { loadAnimals(); }
    public static void loadAllPlayerData() { loadAnimals(); }
    public static void loadAnimals() {
        ownership = new AnimalOwnershipStore(java.nio.file.Path.of("plugins/BreedingBuddies/Data"));
        AnimalIdentity.open(ownership);
        rebuildAnimals();
    }
    static void rebuildAnimals() {
        playerAnimals.clear(); unownedAnimals.clear();
        if (!ownershipReady()) return;
        for (var entry : ownership.rows.entrySet()) {
            if (ownership.held.containsKey(entry.getKey())) continue;
            try {
                FarmAnimal animal = AnimalCodec.load(entry.getValue());
                if (animal.getOwnerRefs().isEmpty()) unownedAnimals.put(animal.getUuid(), animal);
                for (UUID character : animal.getOwnerRefs().keySet()) playerAnimals.computeIfAbsent(character, ignored -> new HashSet<>()).add(animal);
            } catch (RuntimeException failure) {
                ownership.held.put(entry.getKey(), "Unloaded animal: " + failure.getMessage());
            }
        }
    }
    static void saveAnimals() {
        if (!ownershipReady()) return;
        Set<FarmAnimal> animals = new HashSet<>(unownedAnimals.values());
        playerAnimals.values().forEach(animals::addAll);
        try { ownership.save(animals); }
        catch (Exception failure) { ownership.ready = false; throw new IllegalStateException("Animal fanout requires recovery", failure); }
    }

	public static Map<UUID, FarmAnimal> getUnownedAnimals() {
		return unownedAnimals;
	}

	public static Map<UUID, Set<FarmAnimal>> getPlayerAnimals() {
		return playerAnimals;
	}

	public static ArrayList<ChunkArea> getChunkAreas() {
		return chunkAreas;
	}
}