package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipStore.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.Outcome;
import net.tfminecraft.tlibs.character.estate.Recipient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HusbandryDecisionEncodingTest {
    @TempDir Path directory;
    final HusbandryEstateTest fixture = new HusbandryEstateTest();

    @Test void fixedTwelveFieldRenderingMatchesAll15552PinnedFormatCombinations() throws Exception {
        var captured = HusbandryEstate.class.getDeclaredMethod("captured", EstateDecision.class);
        captured.setAccessible(true);
        var times = List.of(Optional.<Instant>empty(), Optional.of(Instant.EPOCH),
                Optional.of(Instant.ofEpochMilli(1700000000123L)), Optional.of(Instant.ofEpochSecond(123, 456789123)));
        var instants = List.of(Instant.EPOCH, Instant.ofEpochMilli(1700000000123L), Instant.ofEpochSecond(123, 456789123));
        List<Optional<CharacterRef>> heirs = List.of(Optional.empty(), Optional.of(fixture.heir));
        List<Optional<UUID>> guilds = List.of(Optional.empty(), Optional.of(UUID.randomUUID()));
        List<Optional<UUID>> realms = List.of(Optional.empty(), Optional.of(UUID.randomUUID()));
        var money = List.of(Recipient.account("char:" + fixture.heir.characterId() + ":bank"), Recipient.account("guild:" + UUID.randomUUID()), Recipient.FROZEN);
        var holdings = List.of(Recipient.character(fixture.heir), Recipient.WAITING, Recipient.FROZEN);
        int cases = 0;
        for (var diedAt : times) for (var decidedAt : instants) for (var heir : heirs)
            for (var guild : guilds) for (var realm : realms) for (var funds : money)
                for (var property : holdings) for (var locks : holdings)
                    for (var letters : List.of(Recipient.character(fixture.heir), Recipient.SENDER))
                        for (long revision : new long[]{0, 1, Long.MAX_VALUE}) {
                            var decision = new EstateDecision(fixture.death, fixture.testator, diedAt, decidedAt,
                                    heir, guild, realm,
                                    funds, property, locks, letters, revision);
                            assertEquals(decision.toString(), captured.invoke(null, decision));
                            cases++;
                        }
        assertEquals(15552, cases);
    }

    @Test void runtimeRecordTextDriftAfterRestartKeepsFullReceiptAndCumulativeIdsUnchanged() throws Exception {
        var original = fixture.decision(1, Recipient.character(fixture.heir));
        Path path = directory.resolve("drift.db");
        Map<String, String> state;
        Outcome expected;
        try (var repository = new HusbandryOwnershipMigrationTest.Repository(path)) {
            var repo = repository.value; var animal = fixture.own(repo, fixture.testator, "owner");
            var connection = repo.ownershipDatabase().getConnection();
            update(connection, "ALTER TABLE character_owners ADD COLUMN future BLOB");
            update(connection, "ALTER TABLE character_owners ADD COLUMN unknown TEXT");
            update(connection, "UPDATE character_owners SET future=?", new byte[]{0, 3, -1});
            expected = fixture.estate(repo).settle(original);
            assertEquals(Outcome.Kind.DONE, expected.kind());
            assertEquals(List.of(animal.uuid().toString()), ((Outcome.Done)expected).movedIds());
            assertEquals(original.toString(), rows(connection, "SELECT decision FROM husbandry_estate_intents").getFirst()[0]);
            state = state(connection);
        }
        try (var repository = new HusbandryOwnershipMigrationTest.Repository(path)) {
            var repo = repository.value; var estate = fixture.estate(repo);
            assertEquals(expected, estate.settle(drift(original, "FutureEstateDecision{same accessors}")));
            assertEquals(state, state(repo.ownershipDatabase().getConnection()));
            assertEquals(expected, estate.settle(drift(fixture.decision(2, Recipient.WAITING), "Future waiting text")));
            assertEquals(state, state(repo.ownershipDatabase().getConnection()));
        }
    }

    @Test void everyNonPropertyFieldChangeAtSameRevisionBlocksEvenWhenRuntimeTextLies() throws Exception {
        var original = fixture.decision(1, Recipient.character(fixture.heir));
        List<EstateDecision> changes = List.of(
                decision(Optional.empty(), original.decidedAt(), original.heir(), original.deathGuild(), original.deathRealm(), original.money(), original.guildLocks(), original.letters()),
                decision(original.diedAt(), Instant.ofEpochMilli(1), original.heir(), original.deathGuild(), original.deathRealm(), original.money(), original.guildLocks(), original.letters()),
                decision(original.diedAt(), original.decidedAt(), Optional.empty(), original.deathGuild(), original.deathRealm(), original.money(), original.guildLocks(), original.letters()),
                decision(original.diedAt(), original.decidedAt(), original.heir(), Optional.of(UUID.randomUUID()), original.deathRealm(), original.money(), original.guildLocks(), original.letters()),
                decision(original.diedAt(), original.decidedAt(), original.heir(), original.deathGuild(), Optional.of(UUID.randomUUID()), original.money(), original.guildLocks(), original.letters()),
                decision(original.diedAt(), original.decidedAt(), original.heir(), original.deathGuild(), original.deathRealm(), Recipient.account("char:" + fixture.heir.characterId() + ":bank"), original.guildLocks(), original.letters()),
                decision(original.diedAt(), original.decidedAt(), original.heir(), original.deathGuild(), original.deathRealm(), original.money(), Recipient.WAITING, original.letters()),
                decision(original.diedAt(), original.decidedAt(), original.heir(), original.deathGuild(), original.deathRealm(), original.money(), original.guildLocks(), Recipient.character(fixture.heir)));
        try (var repository = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("authority.db"))) {
            var repo = repository.value; fixture.own(repo, fixture.testator, "owner"); var estate = fixture.estate(repo);
            assertEquals(Outcome.Kind.DONE, estate.settle(original).kind());
            var before = state(repo.ownershipDatabase().getConnection());
            for (var changed : changes) {
                assertEquals(Outcome.Kind.BLOCKED, estate.settle(changed).kind());
                assertEquals(before, state(repo.ownershipDatabase().getConnection()));
                assertEquals(Outcome.Kind.BLOCKED, estate.settle(drift(changed, original.toString())).kind());
                assertEquals(before, state(repo.ownershipDatabase().getConnection()));
            }
        }
    }

    @Test void unknownKindInAnyPartBlocksBeforeWritesIncludingWaitingAndSelfRecipients() throws Exception {
        try (var repository = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("unknown.db"))) {
            var repo = repository.value; fixture.own(repo, fixture.testator, "owner"); var estate = fixture.estate(repo);
            var before = state(repo.ownershipDatabase().getConnection());
            Recipient.Character unknown = mock(Recipient.Character.class);
            when(unknown.kind()).thenReturn(null);
            when(unknown.character()).thenReturn(Optional.of(fixture.heir));
            for (String field : List.of("money", "property", "guildLocks", "letters", "waiting-money", "self-letters")) {
                var original = fixture.decision(1, field.equals("waiting-money") ? Recipient.WAITING
                        : Recipient.character(field.equals("self-letters") ? fixture.testator : fixture.heir));
                var invalid = drift(original, original.toString());
                switch (field) {
                    case "money", "waiting-money" -> doReturn(unknown).when(invalid).money();
                    case "property" -> doReturn(unknown).when(invalid).property();
                    case "guildLocks" -> doReturn(unknown).when(invalid).guildLocks();
                    default -> doReturn(unknown).when(invalid).letters();
                }
                assertEquals(Outcome.Kind.BLOCKED, estate.settle(invalid).kind(), field);
                assertEquals(before, state(repo.ownershipDatabase().getConnection()), field);
            }
        }
    }

    private EstateDecision decision(Optional<Instant> diedAt, Instant decidedAt,
            Optional<CharacterRef> heir, Optional<UUID> guild,
            Optional<UUID> realm, Recipient money, Recipient locks, Recipient letters) {
        return new EstateDecision(fixture.death, fixture.testator, diedAt, decidedAt, heir, guild, realm,
                money, Recipient.character(fixture.heir), locks, letters, 1);
    }

    private EstateDecision drift(EstateDecision original, String text) {
        EstateDecision result = mock(EstateDecision.class,
                withSettings().defaultAnswer(call -> call.getMethod().invoke(original, call.getArguments())));
        doReturn(text).when(result).toString();
        return result;
    }

    private Map<String, String> state(Connection connection) throws Exception {
        Map<String, String> result = new TreeMap<>();
        for (String table : List.of("character_owners", "husbandry_estate_intents", "husbandry_estate_slots", "husbandry_estate_owner_archive")) {
            if (scalar(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name=? AND type='table'", table) == 0) {
                result.put(table, "absent"); continue;
            }
            var columns = rows(connection, "PRAGMA table_info(" + table + ")").stream().map(row -> "quote(\"" + row[1] + "\")").toList();
            result.put(table, Arrays.deepToString(rows(connection, "SELECT " + String.join(",", columns) + " FROM " + table + " ORDER BY rowid").toArray()));
        }
        return result;
    }
}
