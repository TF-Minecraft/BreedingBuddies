package net.tfminecraft.breedingbuddies.husbandry;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads saved entity chunks ({@code entities/r.X.Z.mca}) to find where unloaded animals are,
 * and player files for animals saved with a rider who logged out ({@code RootVehicle}).
 * Runs off the main thread; it only reads files.
 */
final class HusbandryEntityScan {

    record WorldDir(String world, File entities) {}

    record Found(String world, int x, int y, int z) {}

    /**
     * {@code ridden} holds animals saved under a logged-out rider.
     * {@code complete} is false when some chunk or player file could not be read, so a missing animal may still exist.
     */
    record Result(Map<UUID, Found> found, Set<UUID> ridden, boolean complete) {}

    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d{1,7})\\.(-?\\d{1,7})\\.mca");
    private static final int SECTOR = 4096;
    private static final int MAX_CHUNK_BYTES = 32 * 1024 * 1024;
    private static final int MAX_REGION_BYTES = 256 * 1024 * 1024;

    private HusbandryEntityScan() {}

    static Result scan(List<WorldDir> worlds, Set<UUID> targets) {
        return scan(worlds, null, targets);
    }

    static Result scan(List<WorldDir> worlds, File playerdata, Set<UUID> targets) {
        Map<UUID, Found> found = new HashMap<>();
        Set<UUID> ridden = new HashSet<>();
        boolean complete = true;
        if (targets.isEmpty()) {
            return new Result(found, ridden, true);
        }
        for (WorldDir world : worlds) {
            if (!world.entities().exists()) {
                continue;
            }
            File[] files = world.entities().listFiles();
            if (files == null) {
                complete = false;
                continue;
            }
            for (File file : files) {
                Matcher name = REGION.matcher(file.getName());
                if (!name.matches()) {
                    continue;
                }
                int regionX = Integer.parseInt(name.group(1));
                int regionZ = Integer.parseInt(name.group(2));
                complete &= scanRegion(world, file, regionX, regionZ, targets, found);
            }
        }
        complete &= scanPlayers(playerdata, targets, ridden);
        return new Result(found, ridden, complete);
    }

    private static boolean scanPlayers(File playerdata, Set<UUID> targets, Set<UUID> ridden) {
        if (playerdata == null || !playerdata.exists()) {
            return true;
        }
        File[] files = playerdata.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return false;
        }
        boolean complete = true;
        for (File file : files) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                    new GZIPInputStream(new ByteArrayInputStream(readBounded(file, MAX_CHUNK_BYTES)))
                            .readNBytes(MAX_CHUNK_BYTES)))) {
                Map<UUID, Found> vehicle = new HashMap<>();
                readPlayer(in, targets, vehicle);
                ridden.addAll(vehicle.keySet());
            } catch (IOException | RuntimeException ex) {
                complete = false;
            }
        }
        return complete;
    }

    private static boolean scanRegion(
            WorldDir world, File file, int regionX, int regionZ, Set<UUID> targets, Map<UUID, Found> found) {
        byte[] data;
        try {
            data = readBounded(file, MAX_REGION_BYTES);
        } catch (IOException ex) {
            return false;
        }
        if (data.length == 0) {
            return true;
        }
        if (data.length < SECTOR * 2) {
            return false;
        }
        boolean complete = true;
        for (int index = 0; index < 1024; index++) {
            int entry = readInt(data, index * 4);
            long sectorOffset = (long) (entry >>> 8) * SECTOR;
            if (sectorOffset == 0) {
                continue;
            }
            if (sectorOffset + 5 > data.length) {
                complete = false;
                continue;
            }
            int offset = (int) sectorOffset;
            try {
                byte[] chunk = chunkBytes(world, data, offset, regionX * 32 + index % 32, regionZ * 32 + index / 32);
                if (chunk == null) {
                    complete = false;
                    continue;
                }
                readEntityChunk(new DataInputStream(new ByteArrayInputStream(chunk)), world.world(), targets, found);
            } catch (IOException | RuntimeException ex) {
                complete = false;
            }
        }
        return complete;
    }

    private static byte[] chunkBytes(WorldDir world, byte[] data, int offset, int chunkX, int chunkZ)
            throws IOException {
        int length = readInt(data, offset);
        int compression = data[offset + 4] & 0xFF;
        byte[] raw;
        if ((compression & 0x80) != 0) {
            compression &= 0x7F;
            raw = readBounded(new File(world.entities(), "c." + chunkX + "." + chunkZ + ".mcc"), MAX_CHUNK_BYTES);
        } else {
            if (length < 1 || (long) offset + 4 + length > data.length) {
                return null;
            }
            raw = new byte[length - 1];
            System.arraycopy(data, offset + 5, raw, 0, raw.length);
        }
        InputStream in = switch (compression) {
            case 1 -> new GZIPInputStream(new ByteArrayInputStream(raw));
            case 2 -> new InflaterInputStream(new ByteArrayInputStream(raw));
            case 3 -> new ByteArrayInputStream(raw);
            default -> null;
        };
        if (in == null) {
            return null;
        }
        try (in) {
            byte[] chunk = in.readNBytes(MAX_CHUNK_BYTES + 1);
            if (chunk.length > MAX_CHUNK_BYTES) {
                throw new IOException("Entity chunk larger than " + MAX_CHUNK_BYTES + " bytes");
            }
            return chunk;
        }
    }

    private static byte[] readBounded(File file, int max) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] data = in.readNBytes(max + 1);
            if (data.length > max) {
                throw new IOException(file.getName() + " is larger than " + max + " bytes");
            }
            return data;
        }
    }

    private static int readInt(byte[] data, int at) {
        return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
                | ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
    }

    // ----------------------------------------------------------------------
    //  NBT: only Entities[].UUID, Pos and Passengers are read, the rest is skipped
    // ----------------------------------------------------------------------

    private static final byte END = 0;
    private static final byte DOUBLE = 6;
    private static final byte LIST = 9;
    private static final byte COMPOUND = 10;
    private static final byte INT_ARRAY = 11;

    private static void readEntityChunk(DataInputStream in, String world, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        if (in.readByte() != COMPOUND) {
            throw new IOException("Entity chunk NBT root is not a compound");
        }
        in.readUTF();
        byte type;
        while ((type = in.readByte()) != END) {
            String key = in.readUTF();
            if (key.equals("Entities")) {
                if (type != LIST) throw new IOException("Invalid Entities tag");
                readEntityList(in, world, targets, found);
            } else {
                skip(in, type);
            }
        }
    }

    private static void readPlayer(DataInputStream in, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        if (in.readByte() != COMPOUND) {
            throw new IOException("Player NBT root is not a compound");
        }
        in.readUTF();
        byte type;
        while ((type = in.readByte()) != END) {
            String key = in.readUTF();
            if (key.equals("RootVehicle")) {
                if (type != COMPOUND) throw new IOException("Invalid RootVehicle tag");
                byte inner;
                while ((inner = in.readByte()) != END) {
                    String innerKey = in.readUTF();
                    if (innerKey.equals("Entity")) {
                        if (inner != COMPOUND) throw new IOException("Invalid root vehicle Entity tag");
                        readEntity(in, "", targets, found);
                    } else {
                        skip(in, inner);
                    }
                }
            } else {
                skip(in, type);
            }
        }
    }

    private static void readEntityList(DataInputStream in, String world, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        byte element = in.readByte();
        int size = in.readInt();
        if (size < 0 || (size > 0 && element != COMPOUND)) {
            throw new IOException("Invalid entity list");
        }
        for (int i = 0; i < size; i++) {
            readEntity(in, world, targets, found);
        }
    }

    private static void readEntity(DataInputStream in, String world, Set<UUID> targets, Map<UUID, Found> found)
            throws IOException {
        UUID uuid = null;
        double[] pos = null;
        byte type;
        while ((type = in.readByte()) != END) {
            String key = in.readUTF();
            if (key.equals("UUID")) {
                if (type != INT_ARRAY) throw new IOException("Invalid entity UUID tag");
                int size = in.readInt();
                if (size == 4) {
                    uuid = new UUID(
                            ((long) in.readInt() << 32) | (in.readInt() & 0xFFFFFFFFL),
                            ((long) in.readInt() << 32) | (in.readInt() & 0xFFFFFFFFL));
                } else {
                    throw new IOException("Invalid entity UUID length");
                }
            } else if (key.equals("Pos")) {
                if (type != LIST) throw new IOException("Invalid entity position tag");
                byte element = in.readByte();
                int size = in.readInt();
                if (element == DOUBLE && size == 3) {
                    pos = new double[] {in.readDouble(), in.readDouble(), in.readDouble()};
                    if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1]) || !Double.isFinite(pos[2])) {
                        throw new IOException("Non-finite entity position");
                    }
                } else {
                    throw new IOException("Invalid entity position list");
                }
            } else if (key.equals("Passengers")) {
                if (type != LIST) throw new IOException("Invalid Passengers tag");
                readEntityList(in, world, targets, found);
            } else {
                skip(in, type);
            }
        }
        if (uuid == null || pos == null) {
            throw new IOException("Entity is missing UUID or position");
        }
        if (targets.contains(uuid)) {
            found.putIfAbsent(uuid, new Found(
                    world, (int) Math.floor(pos[0]), (int) Math.floor(pos[1]), (int) Math.floor(pos[2])));
        }
    }

    private static void skip(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(in.readInt());
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {
                byte element = in.readByte();
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    skip(in, element);
                }
            }
            case 10 -> {
                byte inner;
                while ((inner = in.readByte()) != END) {
                    in.skipNBytes(in.readUnsignedShort());
                    skip(in, inner);
                }
            }
            case 11 -> in.skipNBytes(4L * in.readInt());
            case 12 -> in.skipNBytes(8L * in.readInt());
            default -> throw new IOException("Unknown NBT tag " + type);
        }
    }
}
