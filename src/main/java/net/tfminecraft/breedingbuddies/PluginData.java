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
		PluginData.saveAllPlayerData();
		PluginData.saveUnownedAnimals();
		PluginData.saveStableChunks();
		Bukkit.getLogger().info("[BreedingBuddies] Saving all data...");
	}

	public static void loadAllData() {
		loadUnownedAnimals();
		loadStableChunks();
		loadAllPlayerData();
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

	public static void saveUnownedAnimals() {
		try {
			File file = new File(UNOWNED_ANIMALS_DATA_DIR);
			file.getParentFile().mkdirs(); // Ensure the directory exists

			JSONArray animalsJsonArray = new JSONArray();

			for (FarmAnimal animal : unownedAnimals.values()) {
				JSONObject animalJson = new JSONObject();
				animalJson.put("uuid", animal.getUuid().toString());
				animalJson.put("name", animal.getName());
				JSONArray ownersJsonArray = new JSONArray();
				for (UUID ownerUUID : animal.getOwnersUuids()) {
					ownersJsonArray.add(ownerUUID.toString());
				}
				animalJson.put("ownersUuids", ownersJsonArray);
				animalJson.put("stableNeeded", animal.isStableNeeded());
				animalJson.put("friendshipPoints", animal.getFriendshipPoints());
				animalJson.put("geneticPoints", animal.getGeneticPoints());
				animalJson.put("state", animal.getState().toString());
				animalJson.put("fed", animal.getFed());
				animalJson.put("cared", animal.getCared());
				animalJson.put("sleptInStable", animal.isSleptInStable());
				animalJson.put("spaceNeeded", animal.isSpaceNeeded());
				animalJson.put("waterNeeded", animal.isWaterNeeded());
				animalJson.put("daysOut", animal.getDaysOut());

				if (animal.getLastRewardCollected() != null) {
					animalJson.put("lastRewardCollected", animal.getLastRewardCollected().getTime());
				} else {
					animalJson.put("lastRewardCollected", null);
				}

				if (animal.getLastUpdated() != null) {
					animalJson.put("lastUpdated", animal.getLastUpdated().getTime());
				} else {
					animalJson.put("lastUpdated", null);
				}

				animalsJsonArray.add(animalJson);
			}

			JSONObject playerJson = new JSONObject();
			playerJson.put("animals", animalsJsonArray);

			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(playerJson.toJSONString());
			fileWriter.flush();
			fileWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public static void loadUnownedAnimals() {
		try {
			File file = new File(UNOWNED_ANIMALS_DATA_DIR);
			if (!file.exists()) {
				return;
			}
			JSONParser parser = new JSONParser();
			JSONObject playerJson = (JSONObject) parser.parse(new FileReader(file));
			JSONArray animalsJsonArray = (JSONArray) playerJson.get("animals");

			for (Object animalObj : animalsJsonArray) {
				JSONObject animalJson = (JSONObject) animalObj;

				UUID animalUUID = UUID.fromString((String) animalJson.get("uuid"));
				String name = (String) animalJson.get("name");
				// Parsear ownersUuids de JSONArray a List<UUID>
				JSONArray ownersJsonArray = (JSONArray) animalJson.get("ownersUuids");
				Set<UUID> ownersUuids = new HashSet<>();
				for (Object ownerObj : ownersJsonArray) {
					ownersUuids.add(UUID.fromString((String) ownerObj));
				}

				// Construir el objeto FarmAnimal
				FarmAnimal farmAnimal = new FarmAnimal(animalUUID, name, ownersUuids);

				farmAnimal.setStableNeeded((boolean) animalJson.get("stableNeeded"));
				farmAnimal.setFriendshipPoints(((Long) animalJson.get("friendshipPoints")).intValue());
				farmAnimal.setGeneticPoints(((Long) animalJson.get("geneticPoints")).intValue());
				farmAnimal.setState(AnimalStates.valueOf((String) animalJson.get("state")));
				farmAnimal.setFed((boolean) animalJson.get("fed"));
				farmAnimal.setCared((boolean) animalJson.get("cared"));
				farmAnimal.setSleptInStable((boolean) animalJson.get("sleptInStable"));
				farmAnimal.setSpaceNeeded((boolean) animalJson.get("spaceNeeded"));
				farmAnimal.setWaterNeeded((boolean) animalJson.get("waterNeeded"));
				farmAnimal.setDaysOut(((Long) animalJson.get("daysOut")).intValue());
				if (animalJson.get("lastRewardCollected") != null) {
					farmAnimal.setLastRewardCollected(new Date((Long) animalJson.get("lastRewardCollected")));
				}
				if (animalJson.get("lastUpdated") != null) {
					farmAnimal.setLastUpdated(new Date((Long) animalJson.get("lastUpdated")));
				}

				unownedAnimals.put(animalUUID, farmAnimal);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Offline owners' animals change too (day changes, escapes, deaths), so every owner is written.
	static void saveAllPlayerData() {
		for (Map.Entry<UUID, Set<FarmAnimal>> entry : playerAnimals.entrySet()) {
			savePlayerData(entry.getKey(), entry.getValue());
		}
	}

	public static void savePlayerData(UUID playerUUID, Set<FarmAnimal> animals) {
		try {
			File file = new File(PLAYER_DATA_DIR, playerUUID.toString() + ".json");
			file.getParentFile().mkdirs(); // Ensure the directory exists

			JSONArray animalsJsonArray = new JSONArray();

			for (FarmAnimal animal : animals) {
				JSONObject animalJson = new JSONObject();
				animalJson.put("uuid", animal.getUuid().toString());
				animalJson.put("name", animal.getName());

				Set<UUID> uniqueOwners = new HashSet<>(animal.getOwnersUuids());
				JSONArray ownersJsonArray = new JSONArray();
				for (UUID ownerUUID : uniqueOwners) {
					ownersJsonArray.add(ownerUUID.toString());
				}

				animalJson.put("ownersUuids", ownersJsonArray);
				animalJson.put("stableNeeded", animal.isStableNeeded());
				animalJson.put("friendshipPoints", animal.getFriendshipPoints());
				animalJson.put("geneticPoints", animal.getGeneticPoints());
				animalJson.put("state", animal.getState().toString());
				animalJson.put("fed", animal.getFed());
				animalJson.put("cared", animal.getCared());
				animalJson.put("sleptInStable", animal.isSleptInStable());
				animalJson.put("spaceNeeded", animal.isSpaceNeeded());
				animalJson.put("waterNeeded", animal.isWaterNeeded());
				animalJson.put("daysOut", animal.getDaysOut());

				if (animal.getLastRewardCollected() != null) {
					animalJson.put("lastRewardCollected", animal.getLastRewardCollected().getTime());
				} else {
					animalJson.put("lastRewardCollected", null);
				}

				if (animal.getLastUpdated() != null) {
					animalJson.put("lastUpdated", animal.getLastUpdated().getTime());
				} else {
					animalJson.put("lastUpdated", null);
				}

				animalsJsonArray.add(animalJson);
			}

			JSONObject playerJson = new JSONObject();
			playerJson.put("animals", animalsJsonArray);

			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(playerJson.toJSONString());
			fileWriter.flush();
			fileWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void loadPlayerData(UUID playerUUID, Map<UUID, FarmAnimal> tempAnimalMap) {
		try {
			File file = new File(PLAYER_DATA_DIR, playerUUID.toString() + ".json");
			if (!file.exists()) {
				return;
			}
			JSONParser parser = new JSONParser();
			JSONObject playerJson = (JSONObject) parser.parse(new FileReader(file));

			JSONArray animalsJsonArray = (JSONArray) playerJson.get("animals");
			Set<FarmAnimal> loadedAnimals = new HashSet<>();

			for (Object animalObj : animalsJsonArray) {
				JSONObject animalJson = (JSONObject) animalObj;

				UUID animalUUID = UUID.fromString((String) animalJson.get("uuid"));

				// Verificar si el animal ya ha sido cargado en esta sesión
				FarmAnimal farmAnimal = tempAnimalMap.get(animalUUID);

				if (farmAnimal == null) {
					String name = (String) animalJson.get("name");

					// Parsear ownersUuids de JSONArray a List<UUID>
					JSONArray ownersJsonArray = (JSONArray) animalJson.get("ownersUuids");
					Set<UUID> ownersUuids = new HashSet<>();
					for (Object ownerObj : ownersJsonArray) {
						ownersUuids.add(UUID.fromString((String) ownerObj));
					}

					// Construir el objeto FarmAnimal
					farmAnimal = new FarmAnimal(animalUUID, name, ownersUuids);
					farmAnimal.setStableNeeded((boolean) animalJson.get("stableNeeded"));
					farmAnimal.setFriendshipPoints(((Long) animalJson.get("friendshipPoints")).intValue());
					farmAnimal.setGeneticPoints(((Long) animalJson.get("geneticPoints")).intValue());
					farmAnimal.setState(AnimalStates.valueOf((String) animalJson.get("state")));
					farmAnimal.setFed((boolean) animalJson.get("fed"));
					farmAnimal.setCared((boolean) animalJson.get("cared"));
					farmAnimal.setSleptInStable((boolean) animalJson.get("sleptInStable"));
					farmAnimal.setSpaceNeeded((boolean) animalJson.get("spaceNeeded"));
					farmAnimal.setWaterNeeded((boolean) animalJson.get("waterNeeded"));

					farmAnimal.setDaysOut(((Long) animalJson.get("daysOut")).intValue());
					if (animalJson.get("lastRewardCollected") != null) {
						farmAnimal.setLastRewardCollected(new Date((Long) animalJson.get("lastRewardCollected")));
					}

					if (animalJson.get("lastUpdated") != null) {
						farmAnimal.setLastUpdated(new Date((Long) animalJson.get("lastUpdated")));
					}

					// Guardar la instancia única en el mapa temporal
					tempAnimalMap.put(animalUUID, farmAnimal);
				}

				// Añadir el animal cargado a la lista de animales del jugador actual
				loadedAnimals.add(farmAnimal);

				// Asegurarse de que el jugador actual está en la lista de propietarios
				if (!farmAnimal.getOwnersUuids().contains(playerUUID)) {
					farmAnimal.getOwnersUuids().add(playerUUID);
				}
			}

			// Agregar los animales cargados al mapa playerAnimals
			playerAnimals.put(playerUUID, loadedAnimals);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void loadAllPlayerData() {
		Map<UUID, FarmAnimal> tempAnimalMap = new HashMap<>();

		// listFiles() is null when the folder is missing or unreadable.
		File[] playerFiles = new File(PLAYER_DATA_DIR).listFiles();
		if (playerFiles == null) {
			return;
		}

		// Recorre cada carpeta (cada jugador) dentro de la carpeta PlayerData
		for (File playerFolder : playerFiles) {
			if (playerFolder.isFile()) {
				String fileName = playerFolder.getName();

				// Verifica y elimina la extensión .json si está presente
				if (fileName.endsWith(".json")) {
					String uuidString = fileName.substring(0, fileName.length() - 5);
					try {
						UUID playerUUID = UUID.fromString(uuidString);
						// Llama a la función loadPlayerData con el UUID del jugador
						loadPlayerData(playerUUID, tempAnimalMap);
					} catch (IllegalArgumentException e) {
						// Maneja el caso en que el nombre de archivo no sea un UUID válido
						Bukkit.getLogger().warning("Invalid player data file: " + fileName);
					}
				}
			}
		}
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