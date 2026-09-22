package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.*;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DayChangeListener implements Listener {
	private static final Object changeDayLock = new Object();
	private static volatile boolean isChangingDay = false;
	private static FileConfiguration numbersConfig;

	public static void setNumbersConfig(FileConfiguration numbersConfig) {
		DayChangeListener.numbersConfig = numbersConfig;
	}

	@EventHandler
	public void onDayChange(DayChangeEvent event) {
		asyncGlobalChangeDay(null, 1);
		try {
			manageChunkUse();
		} catch (Error e) {
			System.out.println("Error while managing chunk use");
		}
	}

	public static void globalChangeDay(UUID excludeUuid, int daysChecked) {
		List<ChunkArea> chunkAreas = PluginData.getChunkAreas();
		Set<UUID> processed = new HashSet<>();

		for (ChunkArea area : chunkAreas) {
			processChunkArea(area, processed, excludeUuid, daysChecked);
		}

		// Sacamos animales únicos de playerAnimals
		Map<UUID, Set<FarmAnimal>> playerAnimals = PluginData.getPlayerAnimals();
		Set<FarmAnimal> uniqueAnimals = new HashSet<>();
		for (Set<FarmAnimal> animals : playerAnimals.values()) {
			uniqueAnimals.addAll(animals);
		}

		// Procesar animales no actualizados (no presentes en processed)
		for (FarmAnimal animal : uniqueAnimals) {
			int friendshipLost = Numbers.friendshipLostNotCaring * daysChecked
					+ Numbers.friendshipLostNotFeeding * daysChecked;

			if (!processed.contains(animal.getUuid())) {
				if (!animal.isStableNeeded()) {
					animal.changeDay(false, false, true, friendshipLost);
				} else {
					animal.changeDay(false, false, false, friendshipLost);
					animal.incrementDaysOut();
				}
			}
			if (animal.getDaysOut() > Numbers.maxIrlDaysOut) {
				animal.setState(AnimalStates.ESCAPED);
				animal.setOwnersUuids(new HashSet<UUID>());
				UnownedAnimalsManager.addUnownedAnimal(animal);
				OwnershipManager.removeAnimal(animal.getUuid());
			}
		}
	}

	private static void processChunkArea(ChunkArea area, Set<UUID> processed, UUID excludeUuid, int daysChecked) {
		boolean containsWater = ChunkManager.containsWater(area);
		boolean spaceNeeded = ChunkManager.spaceNeeded(area);
		AtomicBoolean hasFarmAnimals = new AtomicBoolean(false);

		for (Chunk chunk : area.getChunks()) {
			processChunk(chunk, !containsWater, spaceNeeded, processed, excludeUuid, daysChecked, hasFarmAnimals);
		}

		if (!hasFarmAnimals.get())
			area.increaseDaysAbandoned();
	}

	private static void processChunk(Chunk chunk, boolean noWater, boolean spaceNeeded, Set<UUID> processed, UUID excludeUuid, int daysChecked, AtomicBoolean hasFarmAnimals) {
		boolean wasForced = chunk.isForceLoaded();
		chunk.setForceLoaded(true);
		try {
			for (Entity entity : chunk.getEntities()) {
				processEntity(entity, noWater, spaceNeeded, processed, excludeUuid, daysChecked, hasFarmAnimals);
			}
		} catch (Exception ex) {
			logChunkError(chunk, ex);
		} finally {
			if (!wasForced) {
				chunk.setForceLoaded(false);
			}
		}
	}

	private static void processEntity(Entity entity, boolean noWater, boolean spaceNeeded, Set<UUID> processed, UUID excludeUuid, int daysChecked, AtomicBoolean hasFarmAnimals) {
		try {
			FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId());
			if (animal != null) {
				if (!hasFarmAnimals.get())
					hasFarmAnimals.set(true);
				if (excludeUuid == null) {
					int friendshipLost = Numbers.friendshipLostNotCaring * daysChecked + Numbers.friendshipLostNotFeeding * daysChecked;
					animal.changeDay(noWater, spaceNeeded, true, friendshipLost);
					processed.add(animal.getUuid());
				} else {
					if (excludeUuid != animal.getUuid()) {
						int friendshipLost = Numbers.friendshipLostNotCaring * daysChecked + Numbers.friendshipLostNotFeeding * daysChecked;
						animal.changeDay(noWater, spaceNeeded, true, friendshipLost);
						processed.add(animal.getUuid());
					}
				}
				animal.setDaysOut(0);
			}
		} catch (Exception ex) {
			logAnimalError(entity, ex);
		}
	}

	private static void manageChunkUse() {
		ArrayList<ChunkArea> chunkAreas = PluginData.getChunkAreas();
		Iterator<ChunkArea> it = chunkAreas.iterator();
		while (it.hasNext()) {
			ChunkArea chunkArea = it.next();
			if (chunkArea.getDaysAbandoned() > Numbers.maxIrlDaysChunkAbandoned) {
				it.remove();
			}
		}
	}

	private static void logChunkError(Chunk chunk, Exception ex) {
		BreedingBuddies.getInstance().getLogger().warning(
				"Error processing chunk " + chunk.getX() + "," + chunk.getZ() + ": " + ex.getMessage()
		);
	}

	private static void logAnimalError(Entity entity, Exception ex) {
		BreedingBuddies.getInstance().getLogger().warning(
				"Error processing animal " + entity.getUniqueId() + ": " + ex.getMessage()
		);
	}

	private static void asyncGlobalChangeDay(UUID excludedEntity, int daysChecked) {
		synchronized (changeDayLock) {
			if (isChangingDay) return;
			isChangingDay = true;
		}
		Plugin bb = BreedingBuddies.getInstance();
		Bukkit.getScheduler().runTaskAsynchronously(bb, () -> {
			try {
				globalChangeDay(excludedEntity, daysChecked);
				PluginData.saveAllData(true);
				updateLastDayChangeDate();
			} catch (Exception e) {
				System.out.println(("Error on DayChange: " + e));
			} finally {
				isChangingDay = false;
			}
		});
	}

	private static void IndividualChangeDay(Entity entity, FarmAnimal animal, int daysChecked) {
		int lostFriendship = Numbers.friendshipLostNotCaring * daysChecked + Numbers.friendshipLostNotFeeding * daysChecked;

		if (!animal.isStableNeeded()) {
			animal.changeDay(false, false, true, lostFriendship);
		} else  {
			Chunk chunk = entity.getLocation().getChunk();
			ChunkArea area = ChunkArea.findAreaForChunk(PluginData.getChunkAreas(), chunk);
			boolean sleptInStable = true;
			boolean containsWater = true;
			boolean spaceNeeded = false;

			if (area == null) {
				sleptInStable = false;
			} else {
				containsWater = ChunkManager.containsWater(area);
				spaceNeeded = ChunkManager.spaceNeeded(area);
			}

			animal.changeDay(!containsWater, spaceNeeded, sleptInStable, lostFriendship);
		}
	}

	public static void changeDayOnInteract(Entity entity, FarmAnimal animal) {
		int daysChecked = daysBetween(getLastChangeDate(), LocalDateTime.now());
		if (daysChecked < 1) return;
		IndividualChangeDay(entity, animal, daysChecked);
		asyncGlobalChangeDay(entity.getUniqueId(), daysChecked);
	}

	public static void changeDayOnReport() {
		synchronized (changeDayLock) {
			if (isChangingDay) return; // evitar doble ejecución si ya está en marcha
			isChangingDay = true;
		}
		try {
			int daysChecked = daysBetween(getLastChangeDate(), LocalDateTime.now());
			if (daysChecked < 1) return;
			globalChangeDay(null, daysChecked);
			PluginData.saveAllData(true);
			updateLastDayChangeDate();
		} catch (Exception e) {
			System.out.println(("Error on DayChange: " + e));
		} finally {
			isChangingDay = false;
		}
	}

	private static LocalDateTime getLastChangeDate() {
		long epoch = numbersConfig.getLong("lastDayChangeDate");
		return LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC);
	}

	private static void updateLastDayChangeDate() {
		long nowEpochSeconds = Instant.now().getEpochSecond();
		numbersConfig.set("lastDayChangeDate", nowEpochSeconds);
		try {
			numbersConfig.save(BreedingBuddies.getInstance().getNumbersFile());
		} catch (IOException e) {
			System.out.println("Error saving lastDayChangeDate on config: " + e.getMessage());
		}
	}

	public static int daysBetween(LocalDateTime startDate, LocalDateTime endDate) {
		return (int) ChronoUnit.DAYS.between(startDate, endDate);
	}

	public static boolean isIsChangingDay() {
		return isChangingDay;
	}
}
