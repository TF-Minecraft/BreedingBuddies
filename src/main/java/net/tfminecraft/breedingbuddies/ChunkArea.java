package net.tfminecraft.breedingbuddies;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Chunk;

public class ChunkArea {
    private Set<Chunk> chunks = new HashSet<>();
    private int daysAbandoned = 0;

    public ChunkArea(Chunk initialChunk) {
        chunks.add(initialChunk);
    }

    public ChunkArea(Set<Chunk> chunks) {
        this.chunks = chunks;
    }

    public Set<Chunk> getChunks() {
        return chunks;
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

    public void addChunk(Chunk chunk) {
        chunks.add(chunk);
    }

    public void addChunks(Set<Chunk> chunks) {
        this.chunks.addAll(chunks);
    }

    public boolean containsChunk(Chunk chunk) {
        return chunks.contains(chunk);
    }

    public boolean isAdjacent(Chunk chunk) {
        for (Chunk c : chunks) {
            if (isAdjacent(c, chunk)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdjacent(Chunk c1, Chunk c2) {
        int dx = Math.abs(c1.getX() - c2.getX());
        int dz = Math.abs(c1.getZ() - c2.getZ());
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    public static ChunkArea findAreaForChunk(ArrayList<ChunkArea> areas, Chunk chunk) {
        if (areas == null || chunk == null) return null;
        for (ChunkArea area : areas) {
            if (area.containsChunk(chunk)) {
                return area;
            }
        }
        return null;
    }
}