package net.tfminecraft.breedingbuddies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.breedingbuddies.ChunkArea;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.PluginData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;

public class ChunkManager {

	public static void addChunk(Chunk newChunk) {
		List<ChunkArea> adjacentAreas = findAdjacentAreas(newChunk);

		if (adjacentAreas.isEmpty()) {
			Set<Chunk> chunks = new HashSet<>();
			chunks.add(newChunk);
			ChunkArea newArea = new ChunkArea(chunks);
			PluginData.getChunkAreas().add(newArea);
		} else {
			ChunkArea combinedArea = new ChunkArea(new HashSet<>());
			for (ChunkArea area : adjacentAreas) {
				combinedArea.addChunks(area.getChunks());
				PluginData.getChunkAreas().remove(area);
			}
			combinedArea.addChunk(newChunk);
			PluginData.getChunkAreas().add(combinedArea);
		}
	}

	private static List<ChunkArea> findAdjacentAreas(Chunk chunk) {
		List<ChunkArea> adjacentAreas = new ArrayList<>();
		for (ChunkArea area : PluginData.getChunkAreas()) {
			if (area.isAdjacent(chunk)) {
				adjacentAreas.add(area);
			}
		}
		return adjacentAreas;
	}


	public static void removeChunk(Chunk oldChunk) {
		ChunkArea areaToRemoveFrom = null;

		for (ChunkArea area : PluginData.getChunkAreas()) {
			if (area.containsChunk(oldChunk)) {
				area.getChunks().remove(oldChunk);
				if (area.getChunks().isEmpty()) {
					areaToRemoveFrom = area;
				}
				break;
			}
		}

		if (areaToRemoveFrom != null) {
			PluginData.getChunkAreas().remove(areaToRemoveFrom);
		}
	}

	public static boolean isStableChunk(Chunk currentChunk) {
		for (ChunkArea area : PluginData.getChunkAreas()) {
			if (area.containsChunk(currentChunk)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isHabitable(ChunkArea chunkArea) {
		return animalsContained(chunkArea) <= Numbers.maxAnimalsPerChunk
				&& containsWater(chunkArea);
	}

	public static int animalsContained(ChunkArea chunkArea) {
		int animalsContained = 0;
		for (Chunk chunk : chunkArea.getChunks()) {
			boolean wasForced = chunk.isForceLoaded();
			chunk.setForceLoaded(true);
			for (Entity entity : chunk.getEntities()) {
				if (entity instanceof Animals) {
					animalsContained++;
				}
			}
			if (!wasForced) {
				chunk.setForceLoaded(false);
			}
		}
		return animalsContained;
	}

	public static boolean containsWater(ChunkArea chunkArea) {
		for (Chunk chunk : chunkArea.getChunks()) {
			boolean wasForced = chunk.isForceLoaded();
			chunk.setForceLoaded(true);
			if (chunk.contains(Bukkit.createBlockData(Material.WATER))
					|| chunk.contains(Bukkit.createBlockData(Material.WATER_CAULDRON))) {
				return true;
			}
			if (!wasForced) {
				chunk.setForceLoaded(false);
			}
		}
		return false;
	}

	public static void forceLoadChunks() {
		for (ChunkArea area : PluginData.getChunkAreas()) {
			for (Chunk chunk : area.getChunks()) {
				chunk.setForceLoaded(true);
			}
		}
	}

	public static void allowUnloadChunks() {
		for (ChunkArea area : PluginData.getChunkAreas()) {
			for (Chunk chunk : area.getChunks()) {
				chunk.setForceLoaded(false);
			}
		}
	}

	public static boolean spaceNeeded(ChunkArea chunkArea) {
		return animalsContained(chunkArea) > Numbers.maxAnimalsPerChunk * chunkArea.getChunks().size();
	}
}