package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HusbandryEntityScanTest {

    private static final UUID COW = UUID.fromString("b3a3b7bb-af8d-48ff-baa4-c75e7b5cd3cf");
    private static final UUID HORSE = UUID.fromString("4a20145e-0b17-4531-a630-e3fb2faa2a50");
    private static final UUID RIDER = UUID.fromString("da6a6b72-b5d9-403c-9af0-0d89eca049fe");
    private static final UUID GONE = UUID.fromString("ad0c0422-8f97-4a4d-96c0-74a20b74383f");

    @Test
    void findsAnimalsAndPassengersInSavedChunks(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        Map<Integer, byte[]> chunks = Map.of(
                0, zlib(chunk(entity(COW, 12.7, 64.0, -3.2))),
                33, gzip(chunk(entity(HORSE, -40.5, 70.0, 900.9, entity(RIDER, -40.5, 71.5, 900.9)))));
        writeRegion(new File(entities, "r.0.-1.mca"), chunks, Map.of());

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("TFMC_Map", entities)), Set.of(COW, RIDER, GONE));

        assertTrue(result.complete());
        assertEquals(new HusbandryEntityScan.Found("TFMC_Map", 12, 64, -4), result.found().get(COW));
        assertEquals(new HusbandryEntityScan.Found("TFMC_Map", -41, 71, 900), result.found().get(RIDER));
        assertFalse(result.found().containsKey(HORSE));
        assertFalse(result.found().containsKey(GONE));
    }

    @Test
    void readsOversizedChunksFromExternalFiles(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        // Chunk index 34 in region (-1, 2) is chunk (-30, 65).
        Files.write(new File(entities, "c.-30.65.mcc").toPath(), zlib(chunk(entity(COW, 1.0, 2.0, 3.0))));
        writeRegion(new File(entities, "r.-1.2.mca"), Map.of(), Map.of(34, 2 | 0x80));

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("world", entities)), Set.of(COW));

        assertTrue(result.complete());
        assertEquals(new HusbandryEntityScan.Found("world", 1, 2, 3), result.found().get(COW));
    }

    @Test
    void unreadableChunksMakeTheScanIncomplete(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        writeRegion(new File(entities, "r.0.0.mca"),
                Map.of(0, zlib(chunk(entity(COW, 0, 0, 0))), 1, new byte[] {1, 2, 3}), Map.of(1, 4));

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("world", entities)), Set.of(COW, GONE));

        assertFalse(result.complete());
        assertTrue(result.found().containsKey(COW));
    }

    @Test
    void truncatedRegionIsIncompleteButEmptyRegionIsNot(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        Files.write(new File(entities, "r.0.0.mca").toPath(), new byte[0]);
        List<HusbandryEntityScan.WorldDir> worlds = List.of(new HusbandryEntityScan.WorldDir("world", entities));
        assertTrue(HusbandryEntityScan.scan(worlds, Set.of(COW)).complete());

        Files.write(new File(entities, "r.0.1.mca").toPath(), new byte[100]);
        assertFalse(HusbandryEntityScan.scan(worlds, Set.of(COW)).complete());
    }

    @Test
    void corruptUuidLengthIsUnreadableNotAnAllocation(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        Body corrupt = out -> {
            out.writeByte(11);
            out.writeUTF("UUID");
            out.writeInt(Integer.MAX_VALUE);
        };
        writeRegion(new File(entities, "r.0.0.mca"), Map.of(0, zlib(chunk(corrupt))), Map.of());

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("world", entities)), Set.of(COW));

        assertFalse(result.complete());
        assertTrue(result.found().isEmpty());
    }

    @Test
    void outOfRangeChunkHeadersAreUnreadable(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        byte[] region = new byte[4096 * 3];
        // Chunk 0 points past the end of the file; chunk 1 claims a 2 GiB payload.
        region[0] = (byte) 0xFF;
        region[1] = (byte) 0xFF;
        region[2] = (byte) 0xFF;
        region[3] = 1;
        region[6] = 2;
        region[7] = 1;
        region[8192] = 0x7F;
        region[8193] = (byte) 0xFF;
        region[8194] = (byte) 0xFF;
        region[8195] = (byte) 0xFF;
        region[8196] = 2;
        Files.write(new File(entities, "r.0.0.mca").toPath(), region);

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("world", entities)), Set.of(COW));

        assertFalse(result.complete());
        assertTrue(result.found().isEmpty());
    }

    @Test
    void animalsUnderALoggedOutRiderCountAsRidden(@TempDir Path dir) throws IOException {
        File entities = dir.resolve("entities").toFile();
        entities.mkdirs();
        File playerdata = dir.resolve("playerdata").toFile();
        playerdata.mkdirs();
        Files.write(new File(playerdata, RIDER + ".dat").toPath(), gzip(player(entity(HORSE, 5.5, 64.0, 5.5))));
        // Paper's backup copy is stale and must not count.
        Files.write(new File(playerdata, RIDER + ".dat_old").toPath(), gzip(player(entity(COW, 1.0, 64.0, 1.0))));

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("TFMC_Map", entities)), playerdata, Set.of(HORSE, COW));

        assertTrue(result.complete());
        assertEquals(Set.of(HORSE), result.ridden());
        assertTrue(result.found().isEmpty());
    }

    @Test
    void unreadablePlayerFilesMakeTheScanIncomplete(@TempDir Path dir) throws IOException {
        File playerdata = dir.resolve("playerdata").toFile();
        playerdata.mkdirs();
        Files.write(new File(playerdata, RIDER + ".dat").toPath(), new byte[] {1, 2, 3});

        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("world", dir.resolve("entities").toFile())),
                playerdata, Set.of(HORSE));

        assertFalse(result.complete());
        assertTrue(result.ridden().isEmpty());
    }

    @Test
    void missingFolderIsSkipped(@TempDir Path dir) {
        HusbandryEntityScan.Result result = HusbandryEntityScan.scan(
                List.of(new HusbandryEntityScan.WorldDir("world", dir.resolve("nope").toFile())), Set.of(COW));
        assertTrue(result.complete());
        assertTrue(result.found().isEmpty());
    }

    // ----------------------------------------------------------------------
    //  Fixture writers
    // ----------------------------------------------------------------------

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static Body entity(UUID uuid, double x, double y, double z, Body... passengers) {
        return out -> {
            out.writeByte(8);
            out.writeUTF("id");
            out.writeUTF("minecraft:cow");
            out.writeByte(10);
            out.writeUTF("Brain");
            out.writeByte(9);
            out.writeUTF("memories");
            out.writeByte(0);
            out.writeInt(0);
            out.writeByte(0);
            out.writeByte(9);
            out.writeUTF("Pos");
            out.writeByte(6);
            out.writeInt(3);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeByte(12);
            out.writeUTF("Longs");
            out.writeInt(1);
            out.writeLong(7L);
            out.writeByte(11);
            out.writeUTF("UUID");
            out.writeInt(4);
            out.writeInt((int) (uuid.getMostSignificantBits() >> 32));
            out.writeInt((int) uuid.getMostSignificantBits());
            out.writeInt((int) (uuid.getLeastSignificantBits() >> 32));
            out.writeInt((int) uuid.getLeastSignificantBits());
            if (passengers.length > 0) {
                out.writeByte(9);
                out.writeUTF("Passengers");
                out.writeByte(10);
                out.writeInt(passengers.length);
                for (Body passenger : passengers) {
                    passenger.write(out);
                }
            }
            out.writeByte(0);
        };
    }

    private static byte[] player(Body vehicle) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(8);
        out.writeUTF("Dimension");
        out.writeUTF("minecraft:overworld");
        out.writeByte(10);
        out.writeUTF("RootVehicle");
        out.writeByte(11);
        out.writeUTF("Attach");
        out.writeInt(4);
        out.writeInt(1);
        out.writeInt(2);
        out.writeInt(3);
        out.writeInt(4);
        out.writeByte(10);
        out.writeUTF("Entity");
        vehicle.write(out);
        out.writeByte(0);
        out.writeByte(0);
        return bytes.toByteArray();
    }

    private static byte[] chunk(Body... entities) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(3);
        out.writeUTF("DataVersion");
        out.writeInt(4556);
        out.writeByte(11);
        out.writeUTF("Position");
        out.writeInt(2);
        out.writeInt(0);
        out.writeInt(0);
        out.writeByte(9);
        out.writeUTF("Entities");
        out.writeByte(10);
        out.writeInt(entities.length);
        for (Body entity : entities) {
            entity.write(out);
        }
        out.writeByte(0);
        return bytes.toByteArray();
    }

    private static byte[] zlib(byte[] raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
            out.write(raw);
        }
        return bytes.toByteArray();
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(raw);
        }
        return bytes.toByteArray();
    }

    /** Payloads default to zlib (2), or gzip (1) when the bytes start with the gzip magic. */
    private static void writeRegion(File file, Map<Integer, byte[]> payloads, Map<Integer, Integer> compression)
            throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int[] header = new int[1024];
        int sector = 2;
        for (int index = 0; index < 1024; index++) {
            byte[] payload = payloads.getOrDefault(index, compression.containsKey(index) ? new byte[0] : null);
            if (payload == null) {
                continue;
            }
            int type = compression.getOrDefault(index,
                    payload.length > 1 && (payload[0] & 0xFF) == 0x1F && (payload[1] & 0xFF) == 0x8B ? 1 : 2);
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(chunk);
            out.writeInt(payload.length + 1);
            out.writeByte(type);
            out.write(payload);
            int sectors = (chunk.size() + 4095) / 4096;
            chunk.write(new byte[sectors * 4096 - chunk.size()]);
            header[index] = (sector << 8) | sectors;
            sector += sectors;
            body.write(chunk.toByteArray());
        }
        ByteArrayOutputStream region = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(region);
        for (int entry : header) {
            out.writeInt(entry);
        }
        out.write(new byte[4096]);
        out.write(body.toByteArray());
        Files.write(file.toPath(), region.toByteArray());
    }
}
