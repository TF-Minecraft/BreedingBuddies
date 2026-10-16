package net.tfminecraft.breedingbuddies.husbandry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.database.SqliteDatabase;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

/** Character slots; the account remains a routing identity and a distinct-animal capacity key. */
final class HusbandryOwnershipStore {
    private final SqliteDatabase database;

    HusbandryOwnershipStore(SqliteDatabase database) {
        this.database = database;
        database.execute("""
                CREATE TABLE IF NOT EXISTS character_owners (
                    animal_uuid TEXT NOT NULL, account_uuid TEXT NOT NULL,
                    character_uuid TEXT NOT NULL, role TEXT NOT NULL,
                    PRIMARY KEY(animal_uuid, character_uuid),
                    FOREIGN KEY(animal_uuid) REFERENCES animals(uuid) ON DELETE CASCADE)
                """);
        database.execute("""
                CREATE TABLE IF NOT EXISTS husbandry_owner_plan (
                    animal_uuid TEXT NOT NULL, account_uuid TEXT NOT NULL,
                    character_uuid TEXT, role TEXT, status TEXT NOT NULL, reason TEXT NOT NULL,
                    PRIMARY KEY(animal_uuid, account_uuid))
                """);
        database.execute("CREATE TABLE IF NOT EXISTS husbandry_owner_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        database.execute("""
                CREATE TABLE IF NOT EXISTS husbandry_estate_intents (
                  death_id TEXT NOT NULL, revision INTEGER NOT NULL, decision TEXT NOT NULL,
                  from_account TEXT NOT NULL, from_character TEXT NOT NULL,
                  to_account TEXT NOT NULL, to_character TEXT NOT NULL, status TEXT NOT NULL,
                  PRIMARY KEY(death_id, revision))
                """);
        database.execute("""
                CREATE TABLE IF NOT EXISTS husbandry_estate_slots (
                  death_id TEXT NOT NULL, revision INTEGER NOT NULL, animal_uuid TEXT NOT NULL,
                  source_role TEXT NOT NULL, recipient_before TEXT, recipient_after TEXT NOT NULL,
                  PRIMARY KEY(death_id, revision, animal_uuid))
                """);
    }

    /** One consistent SQLite snapshot, archive, reviewed plan and publication in one FULL transaction. */
    boolean migrate(BooleanSupplier ready, Function<UUID, Optional<CharacterRef>> heirs) {
        if (completed()) return true;
        if (!ready.getAsBoolean()) return false;
        database.runTransaction(connection -> {
            try {
                // CTAS preserves every legacy owner column and has no cascading foreign keys.
                update(connection, "CREATE TABLE husbandry_owner_archive AS SELECT * FROM owners");
                if (scalar(connection, "SELECT COUNT(*) FROM owners")
                        != scalar(connection, "SELECT COUNT(*) FROM husbandry_owner_archive")) {
                    throw new SQLException("Owner archive verification failed");
                }
                for (String action : List.of("INSERT", "UPDATE", "DELETE")) {
                    update(connection, "CREATE TRIGGER husbandry_archive_" + action
                            + " BEFORE " + action + " ON husbandry_owner_archive BEGIN "
                            + "SELECT RAISE(ABORT, 'Immutable husbandry ownership archive'); END");
                }
                List<String[]> source = rows(connection,
                        "SELECT animal_uuid, player_uuid, role FROM husbandry_owner_archive");
                for (String[] row : source) {
                    CharacterRef destination = null;
                    String reason = "No reviewed heir";
                    try {
                        UUID animal = UUID.fromString(row[0]);
                        UUID account = UUID.fromString(row[1]);
                        destination = heirs.apply(account).filter(ref -> ref.ownerId().equals(account)).orElse(null);
                        if (!recognized(row[2])) {
                            destination = null;
                            reason = "Unknown owner role";
                        } else if (scalar(connection, "SELECT COUNT(*) FROM animals WHERE uuid = ?", animal.toString()) == 0) {
                            destination = null;
                            reason = "Animal row missing";
                        }
                    } catch (IllegalArgumentException ex) {
                        reason = "Malformed legacy identity";
                    }
                    if (destination != null) {
                        String[] existing = slot(connection, row[0], destination.characterId().toString());
                        if (existing != null && (!destination.ownerId().toString().equals(existing[0])
                                || !recognized(existing[1]))) {
                            destination = null;
                            reason = "Conflicting character slot";
                        } else {
                            put(connection, row[0], destination,
                                    existing == null ? row[2] : stronger(row[2], existing[1]));
                        }
                    }
                    update(connection, "INSERT INTO husbandry_owner_plan VALUES (?, ?, ?, ?, ?, ?)",
                            row[0], row[1], destination == null ? null : destination.characterId().toString(), row[2],
                            destination == null ? "held" : "resolved", destination == null ? reason : "");
                }
                update(connection, "INSERT INTO husbandry_owner_meta VALUES ('completed', '1')");
            } catch (SQLException ex) {
                throw new SqliteDatabaseException("Could not migrate husbandry owners", ex);
            }
        });
        return true;
    }

    boolean completed() {
        return count("SELECT COUNT(*) FROM husbandry_owner_meta WHERE key = 'completed'") != 0;
    }

    void put(HusbandryOwner owner) {
        if (owner == null || owner.animalUuid() == null || owner.playerUuid() == null) return;
        if (owner.characterUuid() == null) throw new IllegalArgumentException("Character ownership is required");
        if (!recognized(owner.role())) throw new IllegalArgumentException("Unrecognized owner role");
        if (estateHeld(owner.animalUuid())) throw new IllegalStateException("Animal estate intent requires staff review");
        database.runTransaction(connection -> {
            try {
                String[] before = slot(connection, owner.animalUuid().toString(), owner.characterUuid().toString());
                if (before != null && (!owner.playerUuid().toString().equals(before[0]) || !recognized(before[1]))) {
                    throw new IllegalArgumentException("Conflicting owner slot");
                }
                put(connection, owner.animalUuid().toString(), owner.ref(), owner.role());
            } catch (SQLException ex) {
                throw new SqliteDatabaseException("Could not write character owner", ex);
            }
        });
    }

    void remove(UUID animal, UUID character) {
        if (animal == null || character == null) return;
        if (estateHeld(animal)) throw new IllegalStateException("Animal estate intent requires staff review");
        database.executeUpdate(
                "DELETE FROM character_owners WHERE animal_uuid = ? AND character_uuid = ?", animal.toString(), character.toString());
    }

    List<HusbandryOwner> owners(UUID animal) {
        if (animal == null) return List.of();
        synchronized (database) {
            try {
                List<HusbandryOwner> result = new ArrayList<>();
                for (String[] row : rows(database.getConnection(),
                        "SELECT account_uuid, character_uuid, role FROM character_owners WHERE animal_uuid = ?", animal.toString())) {
                    // Unknown/malformed current slots remain durable and block unowned claims.
                    try {
                        if (recognized(row[2])) result.add(new HusbandryOwner(animal,
                                UUID.fromString(row[0]), UUID.fromString(row[1]), row[2]));
                    } catch (IllegalArgumentException ignored) { }
                }
                return result;
            } catch (SQLException ex) {
                throw new SqliteDatabaseException("Could not read character owners", ex);
            }
        }
    }

    boolean hasAny(UUID animal) {
        return animal != null && count("""
                SELECT COUNT(*) FROM (
                  SELECT animal_uuid FROM character_owners WHERE animal_uuid = ?
                  UNION SELECT o.animal_uuid FROM owners o LEFT JOIN husbandry_owner_plan p
                    ON p.animal_uuid = o.animal_uuid AND p.account_uuid = o.player_uuid
                    WHERE o.animal_uuid = ? AND (p.status IS NULL OR p.status = 'held'))
                """, animal.toString(), animal.toString()) != 0;
    }

    boolean held(UUID animal) {
        if (animal == null) return false;
        if (count("SELECT COUNT(*) FROM character_owners WHERE animal_uuid = ?", animal.toString()) != owners(animal).size()) return true;
        return count("""
                SELECT COUNT(*) FROM owners o LEFT JOIN husbandry_owner_plan p
                  ON p.animal_uuid = o.animal_uuid AND p.account_uuid = o.player_uuid
                  WHERE o.animal_uuid = ? AND (p.status IS NULL OR p.status = 'held')
                """, animal.toString()) != 0;
    }

    int countAccount(UUID account) {
        if (account == null) return 0;
        return count("""
                SELECT COUNT(*) FROM (
                  SELECT animal_uuid FROM character_owners WHERE account_uuid = ?
                  UNION SELECT o.animal_uuid FROM owners o LEFT JOIN husbandry_owner_plan p
                    ON p.animal_uuid = o.animal_uuid AND p.account_uuid = o.player_uuid
                    WHERE o.player_uuid = ? AND (p.status IS NULL OR p.status = 'held'))
                """, account.toString(), account.toString());
    }

    boolean accountHas(UUID account, UUID animal) {
        return account != null && animal != null && count("""
                SELECT COUNT(*) FROM (
                  SELECT animal_uuid FROM character_owners WHERE animal_uuid = ? AND account_uuid = ?
                  UNION SELECT o.animal_uuid FROM owners o LEFT JOIN husbandry_owner_plan p
                    ON p.animal_uuid = o.animal_uuid AND p.account_uuid = o.player_uuid
                    WHERE o.animal_uuid = ? AND o.player_uuid = ? AND (p.status IS NULL OR p.status = 'held'))
                """, animal.toString(), account.toString(), animal.toString(), account.toString()) != 0;
    }

    boolean estateHeld(UUID animal) {
        return animal != null && count("""
                SELECT COUNT(*) FROM husbandry_estate_intents i
                WHERE i.status != 'done' AND (
                  EXISTS (SELECT 1 FROM husbandry_estate_slots s WHERE s.death_id = i.death_id
                    AND s.revision = i.revision AND s.animal_uuid = ?)
                  OR EXISTS (SELECT 1 FROM character_owners o WHERE o.animal_uuid = ?
                    AND o.character_uuid = i.from_character))
                """, animal.toString(), animal.toString()) != 0;
    }

    int heldCount() {
        return count("SELECT COUNT(*) FROM husbandry_owner_plan WHERE status = 'held'");
    }

    int count(String sql, Object... parameters) {
        synchronized (database) {
            try { return scalar(database.getConnection(), sql, parameters); }
            catch (SQLException ex) { throw new SqliteDatabaseException("Could not count owner slots", ex); }
        }
    }

    static boolean recognized(String role) { return "owner".equals(role) || "coowner".equals(role); }
    static String stronger(String left, String right) { return "owner".equals(left) || "owner".equals(right) ? "owner" : "coowner"; }

    static void put(Connection connection, String animal, CharacterRef ref, String role) throws SQLException {
        update(connection, """
                INSERT INTO character_owners (animal_uuid, account_uuid, character_uuid, role) VALUES (?, ?, ?, ?)
                ON CONFLICT(animal_uuid, character_uuid) DO UPDATE SET role = excluded.role
                """, animal, ref.ownerId().toString(), ref.characterId().toString(), role);
    }

    static String[] slot(Connection connection, String animal, String character) throws SQLException {
        List<String[]> rows = rows(connection,
                "SELECT account_uuid, role FROM character_owners WHERE animal_uuid = ? AND character_uuid = ?", animal, character);
        return rows.isEmpty() ? null : rows.get(0);
    }

    static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = statement(connection, sql, parameters)) { return statement.executeUpdate(); }
    }

    static int scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = statement(connection, sql, parameters); ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    static List<String[]> rows(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = statement(connection, sql, parameters); ResultSet result = statement.executeQuery()) {
            List<String[]> rows = new ArrayList<>();
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                String[] row = new String[columns];
                for (int i = 0; i < columns; i++) row[i] = result.getString(i + 1);
                rows.add(row);
            }
            return rows;
        }
    }

    private static PreparedStatement statement(Connection connection, String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            return statement;
        } catch (SQLException ex) {
            statement.close();
            throw ex;
        }
    }
}
