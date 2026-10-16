package net.tfminecraft.breedingbuddies.husbandry;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.Estates;
import net.tfminecraft.tlibs.character.estate.Holdings;
import net.tfminecraft.tlibs.character.estate.Outcome;
import net.tfminecraft.tlibs.character.estate.Recipient;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

import static net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipStore.*;

/** A whole estate's slots, collision merges and receipts commit together; no capacity rejection. */
final class HusbandryEstate implements EstateParticipant {
    static final String ID = "cooking-animals";
    private final HusbandryRepository repository;
    private final Logger logger;
    Predicate<EstateDecision> guard = Estates::guardHolds;
    Supplier<List<EstateDecision>> decided = Estates::decided;
    interface Reporter { boolean report(UUID death, String id, long revision, Outcome outcome); }
    Reporter reporter = Estates::report;
    private final Map<UUID, Map.Entry<Long, Outcome>> reported = new HashMap<>();

    HusbandryEstate(HusbandryRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;

    }

    static void start(Plugin plugin, HusbandryRepository repository) {
        HusbandryEstate estate = new HusbandryEstate(repository, plugin.getLogger());
        plugin.getServer().getServicesManager().register(EstateParticipant.class, estate, plugin, ServicePriority.Normal);
        plugin.getServer().getScheduler().runTaskTimer(plugin, estate::reconcile, 2, 20L * 60 * 60);
    }

    @Override public String id() { return ID; }

    @Override public Outcome settle(EstateDecision decision) {
        if (!guard.test(decision)) return Outcome.retry("Animal estate guard does not hold");
        var db = repository.ownershipDatabase();
        try {
            String capturedDecision = captured(decision);
            if (decision.property().character().isEmpty()) return receipts(decision);
            CharacterRef to = decision.property().character().orElseThrow();
            if (to.equals(decision.testator())) return receipts(decision);
            db.runTransaction(connection -> {
                try {
                    if (scalar(connection, "SELECT COUNT(*) FROM husbandry_estate_intents WHERE status != 'done' AND (death_id = ? OR from_character = ?)",
                            decision.deathId().toString(), decision.testator().characterId().toString()) != 0) {
                        throw new Held("Incomplete animal estate intent requires staff review");
                    }
                    validateReceipts(connection, decision);
                    if (scalar(connection, "SELECT COUNT(*) FROM husbandry_estate_intents WHERE death_id = ? AND revision = ?",
                            decision.deathId().toString(), decision.revision()) != 0) return;
                    List<String[]> source = rows(connection,
                            "SELECT animal_uuid, account_uuid, role FROM character_owners WHERE character_uuid = ?",
                            decision.testator().characterId().toString());
                    update(connection, "INSERT INTO husbandry_estate_intents VALUES (?, ?, ?, ?, ?, ?, ?, 'prepared')",
                            decision.deathId().toString(), decision.revision(), capturedDecision,
                            decision.testator().ownerId().toString(), decision.testator().characterId().toString(),
                            to.ownerId().toString(), to.characterId().toString());
                    for (String[] row : source) {
                        if (!row[1].equals(decision.testator().ownerId().toString()) || !recognized(row[2])) {
                            throw new Held("Conflicting testator slot for animal " + row[0]);
                        }
                        String[] recipient = slot(connection, row[0], to.characterId().toString());
                        if (recipient != null && (!recipient[0].equals(to.ownerId().toString()) || !recognized(recipient[1]))) {
                            throw new Held("Conflicting recipient slot for animal " + row[0]);
                        }
                        String role = recipient == null ? row[2] : stronger(row[2], recipient[1]);
                        if (!guard.test(decision)) throw new GuardChanged();
                        archiveSlot(connection, decision, row[0], decision.testator().characterId(), "source");
                        if (recipient != null) archiveSlot(connection, decision, row[0], to.characterId(), "recipient");
                        put(connection, row[0], to, role);
                        if (!to.equals(decision.testator())) update(connection,
                                "DELETE FROM character_owners WHERE animal_uuid = ? AND character_uuid = ? AND account_uuid = ?",
                                row[0], decision.testator().characterId().toString(), decision.testator().ownerId().toString());
                        update(connection, "INSERT INTO husbandry_estate_slots VALUES (?, ?, ?, ?, ?, ?)",
                                decision.deathId().toString(), decision.revision(), row[0], row[2],
                                recipient == null ? null : recipient[1], role);
                    }
                    if (!guard.test(decision)) throw new GuardChanged();
                    update(connection, "UPDATE husbandry_estate_intents SET status = 'done' WHERE death_id = ? AND revision = ?",
                            decision.deathId().toString(), decision.revision());
                } catch (SQLException ex) {
                    throw new SqliteDatabaseException("Could not commit animal estate", ex);
                }
            });
            return receipts(decision);
        } catch (Held ex) {
            return Outcome.blocked(ex.getMessage());
        } catch (GuardChanged ex) {
            return Outcome.retry("Animal estate changed; transaction rolled back");
        } catch (RuntimeException | SQLException ex) {
            logger.warning("Animal estate rolled back: " + ex.getMessage());
            return Outcome.retry("Could not save animal estate: " + ex.getMessage());
        }
    }

    /** Completed transfers remain visible even when a later property revision is frozen or waiting. */
    private Outcome receipts(EstateDecision decision) throws SQLException {
        var db = repository.ownershipDatabase();
        synchronized (db) {
            Connection connection = db.getConnection();
            if (scalar(connection, "SELECT COUNT(*) FROM husbandry_estate_intents WHERE status != 'done' AND (death_id = ? OR from_character = ?)",
                    decision.deathId().toString(), decision.testator().characterId().toString()) != 0) {
                throw new Held("Incomplete animal estate intent requires staff review");
            }
            validateReceipts(connection, decision);
            List<String> moved = rows(connection,
                    "SELECT DISTINCT animal_uuid FROM husbandry_estate_slots WHERE death_id = ? ORDER BY animal_uuid",
                    decision.deathId().toString()).stream().map(row -> row[0]).toList();
            return moved.isEmpty() ? Outcome.done() : Outcome.done(Map.of("animals", moved.size()), moved);
        }
    }

    /** A restored source cannot become an untouched holding merely by changing the revision. */
    private static void validateReceipts(Connection connection, EstateDecision decision) throws SQLException {
        List<String[]> saved = rows(connection,
                "SELECT revision, decision, from_account, from_character, to_account, to_character FROM husbandry_estate_intents WHERE death_id = ?",
                decision.deathId().toString());
        for (String[] row : saved) {
            long revision;
            try { revision = Long.parseLong(row[0]); }
            catch (RuntimeException invalid) { throw new Held("Animal receipt revision is invalid"); }
            if (revision < 0 || row[1].isBlank()) throw new Held("Animal receipt decision envelope is invalid");
            if (!decision.testator().ownerId().toString().equals(row[2])
                    || !decision.testator().characterId().toString().equals(row[3]))
                throw new Held("Animal receipt testator conflicts with this death");
            CharacterRef recipient;
            try { recipient = new CharacterRef(UUID.fromString(row[4]), UUID.fromString(row[5])); }
            catch (RuntimeException invalid) { throw new Held("Animal receipt recipient is invalid"); }
            if (!row[1].startsWith("EstateDecision[deathId=" + decision.deathId() + ", testator=" + refText(decision.testator()) + ", diedAt=")
                    || !row[1].contains(", property=Character[ref=" + refText(recipient) + "], guildLocks=")
                    || !row[1].endsWith(", revision=" + revision + "]"))
                throw new Held("Historical animal receipt conflicts with its captured decision");
            List<String[]> original = scalar(connection,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'husbandry_estate_owner_archive'") == 0
                    ? List.of() : rows(connection, """
                    SELECT animal_uuid, account_uuid, character_uuid FROM husbandry_estate_owner_archive
                    WHERE estate_death_id = ? AND estate_revision = ? AND estate_position = 'source'
                    """, decision.deathId().toString(), revision);
            for (String[] slot : original) {
                if (!row[2].equals(slot[1]) || !row[3].equals(slot[2]))
                    throw new Held("Animal receipt source archive conflicts with its full reference");
            }
            List<String> plannedIds = original.stream().map(slot -> slot[0]).toList();
            List<String> movedIds = rows(connection,
                    "SELECT animal_uuid FROM husbandry_estate_slots WHERE death_id = ? AND revision = ?",
                    decision.deathId().toString(), revision).stream().map(slot -> slot[0]).toList();
            if (plannedIds.size() != new java.util.HashSet<>(plannedIds).size()
                    || !new java.util.HashSet<>(plannedIds).equals(new java.util.HashSet<>(movedIds)))
                throw new Held("Animal receipt IDs conflict with the immutable source archive");
            if (revision == decision.revision()) {
                if (!captured(decision).equals(row[1])
                        || !decision.property().character().equals(java.util.Optional.of(recipient)))
                    throw new Held("Decision conflicts with stored animal receipt");
                if (scalar(connection, "SELECT COUNT(*) FROM character_owners WHERE character_uuid = ?",
                        decision.testator().characterId().toString()) != 0)
                    throw new Held("Completed animal receipt conflicts with remaining property; review a new revision");
            }
        }
        if (scalar(connection, """
                SELECT COUNT(*) FROM character_owners o JOIN husbandry_estate_slots s ON s.animal_uuid = o.animal_uuid
                WHERE s.death_id = ? AND o.character_uuid = ?
                """, decision.deathId().toString(), decision.testator().characterId().toString()) != 0)
            throw new Held("Previously transferred animal reappeared at the testator; preserve receipts for review");
    }

    /** Fixed text of all twelve TLibs 2.6.0 fields; byte-identical to its Java 21 record text. */
    private static String captured(EstateDecision decision) {
        return "EstateDecision[deathId=" + decision.deathId() + ", testator=" + refText(decision.testator())
                + ", diedAt=" + optionalText(decision.diedAt().map(Instant::toString))
                + ", decidedAt=" + decision.decidedAt()
                + ", heir=" + optionalText(decision.heir().map(HusbandryEstate::refText))
                + ", deathGuild=" + optionalText(decision.deathGuild().map(UUID::toString))
                + ", deathRealm=" + optionalText(decision.deathRealm().map(UUID::toString))
                + ", money=" + partText(decision.money()) + ", property=" + partText(decision.property())
                + ", guildLocks=" + partText(decision.guildLocks()) + ", letters=" + partText(decision.letters())
                + ", revision=" + decision.revision() + "]";
    }

    private static String refText(CharacterRef ref) {
        return "CharacterRef[ownerId=" + ref.ownerId() + ", characterId=" + ref.characterId() + "]";
    }

    private static String optionalText(Optional<String> value) {
        return value.map(text -> "Optional[" + text + "]").orElse("Optional.empty");
    }

    private static String partText(Recipient recipient) {
        Recipient.Kind kind = recipient.kind();
        if (kind == Recipient.Kind.ACCOUNT) return "Account[accountId=" + recipient.account().orElseThrow() + "]";
        if (kind == Recipient.Kind.CHARACTER) return "Character[ref=" + refText(recipient.character().orElseThrow()) + "]";
        if (kind == Recipient.Kind.WAITING) return "Waiting[]";
        if (kind == Recipient.Kind.FROZEN) return "Frozen[]";
        if (kind == Recipient.Kind.SENDER) return "Sender[]";
        throw new Held("Unsupported estate decision part " + kind + "; BreedingBuddies must be updated");
    }

    /** Independent raw rows retain unknown nullable/BLOB metadata even after the source slot is deleted. */
    private static void archiveSlot(Connection connection, EstateDecision decision, String animal, UUID character,
            String position) throws SQLException {
        update(connection, "CREATE TABLE IF NOT EXISTS husbandry_estate_owner_archive AS SELECT * FROM character_owners WHERE 0");
        List<String> columns = rows(connection, "PRAGMA table_info(character_owners)").stream().map(row -> row[1]).toList();
        List<String> saved = rows(connection, "PRAGMA table_info(husbandry_estate_owner_archive)").stream().map(row -> row[1]).toList();
        for (String metadata : List.of("estate_death_id", "estate_revision", "estate_position")) {
            if (columns.contains(metadata)) throw new Held("Owner schema uses reserved estate archive column " + metadata);
            if (!saved.contains(metadata)) update(connection, "ALTER TABLE husbandry_estate_owner_archive ADD COLUMN " + quoted(metadata));
        }
        for (String column : columns) {
            if (!saved.contains(column)) update(connection, "ALTER TABLE husbandry_estate_owner_archive ADD COLUMN " + quoted(column));
        }
        String fields = columns.stream().map(HusbandryEstate::quoted).collect(java.util.stream.Collectors.joining(","));
        update(connection, "INSERT INTO husbandry_estate_owner_archive (" + fields
                + ",estate_death_id,estate_revision,estate_position) SELECT " + fields
                + ",?,?,? FROM character_owners WHERE animal_uuid = ? AND character_uuid = ?",
                decision.deathId().toString(), decision.revision(), position, animal, character.toString());
    }

    private static String quoted(String column) { return "\"" + column.replace("\"", "\"\"") + "\""; }

    @Override public Holdings describe(UUID characterId) {
        var db = repository.ownershipDatabase();
        try {
            synchronized (db) {
                List<String> labels = rows(db.getConnection(),
                        "SELECT animal_uuid FROM character_owners WHERE character_uuid = ? ORDER BY animal_uuid", characterId.toString())
                        .stream().map(row -> "Animal " + row[0]).toList();
                return labels.isEmpty() ? Holdings.NONE : new Holdings(Map.of("animals", labels.size()), labels);
            }
        } catch (SQLException ex) {
            throw new SqliteDatabaseException("Could not describe animal holdings", ex);
        }
    }

    void reconcile() {
        for (EstateDecision decision : decided.get()) {
            try {
                if (!guard.test(decision)) continue;
                Outcome outcome = settle(decision);
                var report = Map.entry(decision.revision(), outcome);
                if (!report.equals(reported.get(decision.deathId()))
                        && reporter.report(decision.deathId(), ID, decision.revision(), outcome)) reported.put(decision.deathId(), report);
            } catch (RuntimeException ex) {
                logger.warning("Could not reconcile animal estate " + decision.deathId() + ": " + ex.getMessage());
            }
        }
    }

    private static final class Held extends RuntimeException { Held(String reason) { super(reason); } }
    private static final class GuardChanged extends RuntimeException { }
}
