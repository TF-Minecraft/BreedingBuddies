package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.*;
import static net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipStore.*;

class HusbandryEstateTest {
    @TempDir Path directory;
    final CharacterRef testator = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final CharacterRef heir = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final CharacterRef coowner = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    EstateDecision decision(long revision, Recipient property) {
        return new EstateDecision(death, testator, Optional.of(Instant.EPOCH), Instant.EPOCH,
                Optional.of(heir), Optional.empty(), Optional.empty(), Recipient.FROZEN, property,
                Recipient.FROZEN, Recipient.SENDER, revision);
    }
    HusbandryAnimal own(HusbandryRepository repo, CharacterRef ref, String role) {
        var animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Daisy");
        animal.setCare(61); animal.setGenetics(871); animal.setHungrySince(9L); animal.setState(HusbandryAnimalState.OWNED);
        repo.saveAnimalAndOwner(animal, new HusbandryOwner(animal.uuid(), ref.ownerId(), ref.characterId(), role));
        return animal;
    }
    HusbandryEstate estate(HusbandryRepository repo) {
        var estate = new HusbandryEstate(repo, Logger.getAnonymousLogger()); estate.guard = decision -> true; return estate;
    }
    @Test void slotTransferPreservesCoownersRoleCollisionsAnimalPayloadAndCumulativeReceipts() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo, testator, "coowner");
            repo.upsertOwner(new HusbandryOwner(animal.uuid(), coowner.ownerId(), coowner.characterId(), "coowner"));
            repo.upsertOwner(new HusbandryOwner(animal.uuid(), heir.ownerId(), heir.characterId(), "owner"));
            repo.upsertSnapshots(Map.of(animal.uuid(), new byte[]{1,7,8}), 9);
            var estate = estate(repo); var decision = decision(1, Recipient.character(heir));
            assertEquals(Map.of("animals", 1), estate.describe(testator.characterId()).counts());
            assertEquals(Holdings.NONE, estate.describe(UUID.randomUUID()));
            var answer = (Outcome.Done)estate.settle(decision);
            assertEquals(List.of(animal.uuid().toString()), answer.movedIds());
            assertEquals(2, repo.listOwners(animal.uuid()).size());
            assertEquals("owner", repo.listOwners(animal.uuid()).stream().filter(o -> o.ref().equals(heir)).findFirst().orElseThrow().role());
            assertTrue(repo.listOwners(animal.uuid()).stream().anyMatch(o -> o.ref().equals(coowner)));
            assertFalse(repo.listOwners(animal.uuid()).stream().anyMatch(o -> o.ref().equals(testator)));
            assertEquals(61, repo.getAnimal(animal.uuid()).orElseThrow().care()); assertArrayEquals(new byte[]{1,7,8}, repo.getSnapshot(animal.uuid()).orElseThrow().data());
            assertEquals(answer, estate.settle(decision));
            assertEquals(answer, estate.settle(decision(7, Recipient.FROZEN)));
            assertEquals(answer, estate.settle(decision(8, Recipient.WAITING)));
            var late = own(repo, testator, "owner");
            assertEquals(Outcome.Kind.DONE, estate.settle(decision(2, Recipient.character(heir))).kind());
            assertEquals(2, ((Outcome.Done)estate.settle(decision(2, Recipient.character(heir)))).movedIds().size());
            assertEquals(Holdings.NONE, estate.describe(testator.characterId()));
            assertEquals(2, repo.countForPlayer(heir.ownerId()));
        }
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            assertEquals(2, ((Outcome.Done)estate(fixture.value).settle(decision(2, Recipient.character(heir)))).movedIds().size());
        }
    }
    @Test void frozenWaitingStaleGuardRevivalAndDeathIdentityCannotPublish() {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo, testator, "owner"); var estate = estate(repo);
            assertEquals(Outcome.done(), estate.settle(decision(1, Recipient.FROZEN)));
            assertEquals(Outcome.done(), estate.settle(decision(2, Recipient.WAITING)));
            estate.guard = d -> false;
            assertEquals(Outcome.Kind.RETRY, estate.settle(decision(3, Recipient.character(heir))).kind());
            assertEquals(testator, repo.listOwners(animal.uuid()).getFirst().ref());
            estate.guard = new java.util.function.Predicate<>() { int checks; public boolean test(EstateDecision d) { return ++checks < 3; } };
            assertEquals(Outcome.Kind.RETRY, estate.settle(decision(3, Recipient.character(heir))).kind());
            assertEquals(testator, repo.listOwners(animal.uuid()).getFirst().ref());
            estate.guard = d -> true; assertEquals(Outcome.Kind.DONE, estate.settle(decision(4, Recipient.character(testator))).kind());
            assertEquals(testator, repo.listOwners(animal.uuid()).getFirst().ref());
        }
    }
    @Test void restoredTransferredSlotBlocksEveryRevisionAndRestartWithoutChangingRowsOrReceipts() throws Exception {
        UUID animalId;
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo, testator, "owner"); animalId = animal.uuid();
            repo.upsertOwner(new HusbandryOwner(animalId, coowner.ownerId(), coowner.characterId(), "coowner"));
            var estate = estate(repo); assertEquals(Outcome.Kind.DONE, estate.settle(decision(1, Recipient.character(heir))).kind());
            repo.upsertOwner(new HusbandryOwner(animalId, testator.ownerId(), testator.characterId(), "owner"));
            var connection = repo.ownershipDatabase().getConnection();
            var owners = rows(connection, "SELECT * FROM character_owners ORDER BY animal_uuid, character_uuid").stream().map(Arrays::asList).toList();
            var intents = rows(connection, "SELECT * FROM husbandry_estate_intents").stream().map(Arrays::asList).toList();
            var slots = rows(connection, "SELECT * FROM husbandry_estate_slots").stream().map(Arrays::asList).toList();
            for (var request : List.of(decision(1, Recipient.character(heir)), decision(2, Recipient.character(coowner)),
                    decision(3, Recipient.WAITING), decision(4, Recipient.FROZEN))) {
                assertEquals(Outcome.Kind.BLOCKED, estate.settle(request).kind());
                assertEquals(owners, rows(connection, "SELECT * FROM character_owners ORDER BY animal_uuid, character_uuid").stream().map(Arrays::asList).toList());
                assertEquals(intents, rows(connection, "SELECT * FROM husbandry_estate_intents").stream().map(Arrays::asList).toList());
                assertEquals(slots, rows(connection, "SELECT * FROM husbandry_estate_slots").stream().map(Arrays::asList).toList());
                assertEquals(61, repo.getAnimal(animalId).orElseThrow().care());
            }
        }
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            assertEquals(Outcome.Kind.BLOCKED, estate(fixture.value).settle(decision(5, Recipient.character(heir))).kind());
            assertEquals(3, fixture.value.listOwners(animalId).size());
        }
    }
    @Test void completedEmptyReceiptRequiresNewRevisionForLateUntouchedHoldings() {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var estate = estate(fixture.value); var request = decision(1, Recipient.character(heir));
            assertEquals(Outcome.done(), estate.settle(request));
            var late = own(fixture.value, testator, "owner");
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(request).kind());
            assertEquals(testator, fixture.value.listOwners(late.uuid()).getFirst().ref());
            assertEquals(List.of(late.uuid().toString()), ((Outcome.Done)estate.settle(decision(2, Recipient.character(heir)))).movedIds());
        }
    }
    @Test void completedReceiptsValidateFullRefsAndSupportedDecisionEnvelope() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo, testator, "owner"); var estate = estate(repo);
            var request = decision(1, Recipient.character(heir)); assertEquals(Outcome.Kind.DONE, estate.settle(request).kind());
            var connection = repo.ownershipDatabase().getConnection();
            Map<String,String> original = Map.of("from_account",testator.ownerId().toString(),"from_character",testator.characterId().toString(),
                    "to_account",heir.ownerId().toString(),"to_character",heir.characterId().toString(),"revision","1","decision",request.toString());
            for (String field : List.of("from_account","from_character","to_account","to_character","revision","decision")) {
                List<String> invalid = field.equals("revision") ? List.of("invalid", "-1") : field.equals("decision") ? List.of("", "wrong") : List.of(UUID.randomUUID().toString());
                for (String value : invalid) {
                    update(connection,"UPDATE husbandry_estate_intents SET " + field + " = ?",value);
                    assertEquals(Outcome.Kind.BLOCKED,estate.settle(request).kind(),field+"="+value);
                    assertEquals(heir,repo.listOwners(animal.uuid()).getFirst().ref());
                    update(connection,"UPDATE husbandry_estate_intents SET " + field + " = ?",original.get(field));
                }
            }
            update(connection,"UPDATE husbandry_estate_intents SET to_account = 'invalid'");
            assertEquals(Outcome.Kind.BLOCKED,estate.settle(decision(2,Recipient.FROZEN)).kind());
        }
    }
    @Test void historicalEnvelopeCorruptionBlocksNewWaitingAndCharacterRevisionsWithoutChangingState() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo,testator,"owner");var estate=estate(repo);
            var request=decision(1,Recipient.character(heir));assertEquals(Outcome.Kind.DONE,estate.settle(request).kind());
            var connection=repo.ownershipDatabase().getConnection();
            Map<String,String> original=Map.of("to_account",heir.ownerId().toString(),"to_character",heir.characterId().toString(),
                    "decision",request.toString(),"revision","1");
            for(String field:List.of("to_account","to_character","decision","revision")) {
                String value=field.equals("decision")?"wrong":field.equals("revision")?"2":UUID.randomUUID().toString();
                update(connection,"UPDATE husbandry_estate_intents SET "+field+" = ?",value);
                var intents=rows(connection,"SELECT * FROM husbandry_estate_intents").stream().map(Arrays::asList).toList();
                var slots=rows(connection,"SELECT * FROM husbandry_estate_slots").stream().map(Arrays::asList).toList();
                var archive=rows(connection,"SELECT * FROM husbandry_estate_owner_archive").stream().map(Arrays::asList).toList();
                for(var next:List.of(decision(3,Recipient.WAITING),decision(4,Recipient.character(coowner)))) {
                    assertEquals(Outcome.Kind.BLOCKED,estate.settle(next).kind(),field);
                    assertEquals(heir,repo.listOwners(animal.uuid()).getFirst().ref());
                    assertEquals(intents,rows(connection,"SELECT * FROM husbandry_estate_intents").stream().map(Arrays::asList).toList());
                    assertEquals(slots,rows(connection,"SELECT * FROM husbandry_estate_slots").stream().map(Arrays::asList).toList());
                    assertEquals(archive,rows(connection,"SELECT * FROM husbandry_estate_owner_archive").stream().map(Arrays::asList).toList());
                }
                update(connection,"UPDATE husbandry_estate_intents SET "+field+" = ?",original.get(field));
            }
        }
    }
    @Test void missingCompletedIdsCannotHideRestoredSourcesFromNewRevisions() throws Exception {
        try(var fixture=new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo=fixture.value;var animal=own(repo,testator,"owner");var estate=estate(repo);
            assertEquals(Outcome.Kind.DONE,estate.settle(decision(1,Recipient.character(heir))).kind());
            var connection=repo.ownershipDatabase().getConnection();update(connection,"DELETE FROM husbandry_estate_slots");
            repo.upsertOwner(new HusbandryOwner(animal.uuid(),testator.ownerId(),testator.characterId(),"owner"));
            var before=rows(connection,"SELECT * FROM character_owners ORDER BY character_uuid").stream().map(Arrays::asList).toList();
            assertEquals(Outcome.Kind.BLOCKED,estate.settle(decision(2,Recipient.character(coowner))).kind());
            assertEquals(before,rows(connection,"SELECT * FROM character_owners ORDER BY character_uuid").stream().map(Arrays::asList).toList());
            assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM husbandry_estate_intents"));
            assertEquals(0,scalar(connection,"SELECT COUNT(*) FROM husbandry_estate_slots"));
            assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM husbandry_estate_owner_archive"));
        }
    }
    @Test void completedReceiptIdsAndArchivedRefsStayCoherentAcrossEveryRevision() throws Exception {
        try(var fixture=new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo=fixture.value;var animal=own(repo,testator,"owner");var estate=estate(repo);
            assertEquals(Outcome.Kind.DONE,estate.settle(decision(1,Recipient.character(heir))).kind());
            var connection=repo.ownershipDatabase().getConnection();
            for(String field:List.of("account_uuid","character_uuid")) {
                update(connection,"UPDATE husbandry_estate_owner_archive SET "+field+" = ?",UUID.randomUUID().toString());
                assertEquals(Outcome.Kind.BLOCKED,estate.settle(decision(2,Recipient.FROZEN)).kind());
                update(connection,"UPDATE husbandry_estate_owner_archive SET "+field+" = ?",field.equals("account_uuid")?testator.ownerId().toString():testator.characterId().toString());
            }
            update(connection,"INSERT INTO husbandry_estate_owner_archive SELECT * FROM husbandry_estate_owner_archive");
            assertEquals(Outcome.Kind.BLOCKED,estate.settle(decision(2,Recipient.FROZEN)).kind());
            update(connection,"DELETE FROM husbandry_estate_owner_archive WHERE rowid=(SELECT MAX(rowid) FROM husbandry_estate_owner_archive)");
            update(connection,"UPDATE husbandry_estate_slots SET animal_uuid = ?",UUID.randomUUID().toString());
            assertEquals(Outcome.Kind.BLOCKED,estate.settle(decision(2,Recipient.FROZEN)).kind());
            assertEquals(heir,repo.listOwners(animal.uuid()).getFirst().ref());
        }
    }
    @Test void faultAfterFirstSlotRollsBackWholeEstateThenRetryMovesExactlyOnce() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var first = own(repo, testator, "owner"); var second = own(repo, testator, "owner");
            var connection = repo.ownershipDatabase().getConnection(); var estate = estate(repo);
            update(connection, "CREATE TRIGGER fail_move BEFORE DELETE ON character_owners WHEN OLD.animal_uuid = '" + second.uuid() + "' BEGIN SELECT RAISE(ABORT,'fault'); END");
            assertEquals(Outcome.Kind.RETRY, estate.settle(decision(1, Recipient.character(heir))).kind());
            assertEquals(testator, repo.listOwners(first.uuid()).getFirst().ref()); assertEquals(testator, repo.listOwners(second.uuid()).getFirst().ref());
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM husbandry_estate_intents"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM husbandry_estate_slots"));
            update(connection, "DROP TRIGGER fail_move");
            assertEquals(2, ((Outcome.Done)estate.settle(decision(1, Recipient.character(heir)))).movedIds().size());
        }
    }
    @Test void unknownRolesAccountsAndIncompleteOrConflictingIntentsAreBlocked() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo, testator, "owner"); var estate = estate(repo); var connection = repo.ownershipDatabase().getConnection();
            update(connection, "UPDATE character_owners SET role = 'unknown'");
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(1, Recipient.character(heir))).kind());
            update(connection, "UPDATE character_owners SET role = 'owner', account_uuid = ?", coowner.ownerId().toString());
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(1, Recipient.character(heir))).kind());
            update(connection, "UPDATE character_owners SET account_uuid = ?", testator.ownerId().toString());
            update(connection, "INSERT INTO character_owners VALUES (?, ?, ?, 'future')", animal.uuid().toString(), heir.ownerId().toString(), heir.characterId().toString());
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(1, Recipient.character(heir))).kind());
            update(connection, "UPDATE character_owners SET role = 'owner', account_uuid = ? WHERE character_uuid = ?", coowner.ownerId().toString(), heir.characterId().toString());
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(1, Recipient.character(heir))).kind());
            update(connection, "DELETE FROM character_owners WHERE character_uuid = ?", heir.characterId().toString());
            assertEquals(Outcome.Kind.DONE, estate.settle(decision(1, Recipient.character(heir))).kind());
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(1, Recipient.character(coowner))).kind());
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(1, Recipient.FROZEN)).kind());
            update(connection, "UPDATE husbandry_estate_intents SET status = 'prepared'");
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(2, Recipient.character(heir))).kind());
            assertEquals(Outcome.Kind.BLOCKED, estate.settle(decision(3, Recipient.FROZEN)).kind());
        }
    }
    @Test void finalGuardFailureRollsBackEmptyPlanAndReconcileReportsPerRevisionAndContinuesFailures() {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var estate = estate(fixture.value); var decision = decision(1, Recipient.character(heir));
            AtomicInteger checks = new AtomicInteger(); estate.guard = d -> checks.incrementAndGet() == 1;
            assertEquals(Outcome.Kind.RETRY, estate.settle(decision).kind());
            estate.guard = d -> true; estate.decided = () -> List.of(decision);
            AtomicInteger reports = new AtomicInteger(); estate.reporter = (death,id,revision,outcome) -> { reports.incrementAndGet(); return true; };
            estate.reconcile(); estate.reconcile(); assertEquals(1, reports.get());
            estate.decided = () -> List.of(decision(2, Recipient.FROZEN)); estate.reconcile(); assertEquals(2, reports.get());
            estate.decided = () -> List.of(decision(3, Recipient.FROZEN)); estate.reporter = (death,id,revision,outcome) -> false; estate.reconcile();
            estate.guard = d -> false; estate.reconcile();
            estate.guard = d -> { throw new IllegalStateException("Unavailable identity"); }; assertDoesNotThrow(estate::reconcile);
        }
    }
    @Test void unreadableHoldingsSchemaIsReportedRatherThanAssumedEmpty() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var estate = estate(fixture.value);
            update(fixture.value.ownershipDatabase().getConnection(), "DROP TABLE character_owners");
            assertThrows(net.tfminecraft.tlibs.database.SqliteDatabaseException.class, () -> estate.describe(testator.characterId()));
        }
    }
    @Test void unknownNullableAndBlobSlotMetadataSurvivesTransferAndLaterSchemaExpansion() throws Exception {
        try (var fixture = new HusbandryOwnershipMigrationTest.Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = own(repo, testator, "owner"); var connection = repo.ownershipDatabase().getConnection();
            update(connection,"ALTER TABLE character_owners ADD COLUMN unknown TEXT");
            update(connection,"ALTER TABLE character_owners ADD COLUMN payload BLOB");
            update(connection,"UPDATE character_owners SET payload = ?", new byte[]{0,3,-1});
            var estate=estate(repo); assertEquals(Outcome.Kind.DONE,estate.settle(decision(1,Recipient.character(heir))).kind());
            assertNull(rows(connection,"SELECT unknown FROM husbandry_estate_owner_archive").getFirst()[0]);
            try(var statement=connection.prepareStatement("SELECT payload FROM husbandry_estate_owner_archive");var result=statement.executeQuery()) {
                result.next(); assertArrayEquals(new byte[]{0,3,-1},result.getBytes(1));
            }
            update(connection,"ALTER TABLE character_owners ADD COLUMN next_column TEXT DEFAULT 'kept'");
            var late=own(repo,testator,"owner");
            assertEquals(Outcome.Kind.DONE,estate.settle(decision(2,Recipient.character(heir))).kind());
            assertEquals("kept",rows(connection,"SELECT next_column FROM husbandry_estate_owner_archive WHERE animal_uuid=?",late.uuid().toString()).getFirst()[0]);
            update(connection,"ALTER TABLE character_owners ADD COLUMN estate_death_id TEXT");
            own(repo,testator,"owner");
            assertEquals(Outcome.Kind.BLOCKED,estate.settle(decision(3,Recipient.character(heir))).kind());
        }
    }
}
