package net.tfminecraft.breedingbuddies;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;
import java.util.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;

final class AnimalFiles {
    private AnimalFiles() {}
    static JSONObject object(String text) throws IOException {
        try {
            new JSONParser().parse(text, new ContentHandler() {
                final Deque<Set<String>> keys = new ArrayDeque<>();
                public void startJSON() {} public void endJSON() {}
                public boolean startObject() { keys.push(new HashSet<>()); return true; }
                public boolean endObject() { keys.pop(); return true; }
                public boolean startObjectEntry(String key) throws ParseException {
                    if (!keys.peek().add(key)) throw new ParseException(ParseException.ERROR_UNEXPECTED_TOKEN, "Duplicate object key " + key);
                    return true;
                }
                public boolean endObjectEntry() { return true; }
                public boolean startArray() { return true; } public boolean endArray() { return true; }
                public boolean primitive(Object value) { return true; }
            });
            return (JSONObject) new JSONParser().parse(text);
        }
        catch (Exception failure) { throw new IOException("Invalid animal document", failure); }
    }
    static JSONObject copy(JSONObject value) { try { return object(value.toJSONString()); } catch (IOException impossible) { throw new IllegalStateException(impossible); } }
    static String read(Path path) throws IOException { return Files.exists(path) ? Files.readString(path) : null; }
    static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        for (Path parent = path.toAbsolutePath().getParent(); parent != null; parent = parent.getParent()) {
            try (FileChannel directory = FileChannel.open(parent, READ)) { directory.force(true); }
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".next");
        try (FileChannel file = FileChannel.open(temporary, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel directory = FileChannel.open(path.getParent(), READ)) { directory.force(true); }
    }
}
