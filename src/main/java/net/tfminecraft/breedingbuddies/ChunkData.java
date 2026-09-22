package net.tfminecraft.breedingbuddies;

import org.bukkit.Chunk;

public class ChunkData {
	private Chunk chunk;
	private int daysAbandoned = 0;
	private int hasWater;
	
	public ChunkData(Chunk chunk) {
		this.chunk = chunk;
	}
	
	public Chunk getChunk() {
        return chunk;
    }
	
	public int getDaysAbandoned() {
		return daysAbandoned;
	}
	
	public void increaseDaysAbandoned() {
		daysAbandoned++;
	}

	public void setDaysAbandoned(int daysAbandoned) {
		this.daysAbandoned = daysAbandoned;
	}
}
