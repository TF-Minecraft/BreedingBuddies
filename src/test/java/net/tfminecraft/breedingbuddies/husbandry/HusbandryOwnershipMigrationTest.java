package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;
import static net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipStore.*;

class HusbandryOwnershipMigrationTest {
    @TempDir Path directory;
    final UUID account = UUID.randomUUID(), otherAccount = UUID.randomUUID(), character = UUID.randomUUID();

    HusbandryAnimal animal(HusbandryRepository repo) {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Daisy");
        animal.setCare(47); animal.setGenetics(901); animal.setHungrySince(23L);
        repo.upsertAnimal(animal); return animal;
    }
    Connection connection(HusbandryRepository repo) { return repo.ownershipDatabase().getConnection(); }
    CharacterRef heir() { return new CharacterRef(account, character); }
    void legacy(HusbandryRepository repo, UUID animal, UUID owner, String role) throws Exception {
        update(connection(repo), "INSERT INTO owners VALUES (?, ?, ?)", animal.toString(), owner.toString(), role);
    }

    @Test void migrationWaitsForReviewedHeirsAndPublishesOnceWithImmutableIndependentArchive() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var r = repo.value; var animal = animal(r); legacy(r, animal.uuid(), account, "owner");
            update(connection(r), "ALTER TABLE owners ADD COLUMN future TEXT");
            update(connection(r), "UPDATE owners SET future = 'preserved'");
            update(connection(r), "ALTER TABLE animals ADD COLUMN future TEXT DEFAULT 'animal-progress'");
            r.upsertSnapshots(java.util.Map.of(animal.uuid(), new byte[]{1,2,3}), 71);
            assertFalse(r.migrateOwners(() -> false, id -> { fail("No heir lookup before readiness"); return Optional.empty(); }));
            assertTrue(r.hasAnyOwner(animal.uuid())); assertTrue(r.hasHeldOwner(animal.uuid()));
            assertEquals(1, r.countForPlayer(account)); assertTrue(r.listOwners(animal.uuid()).isEmpty());
            assertTrue(r.migrateOwners(() -> true, id -> Optional.of(heir())));
            assertEquals(heir(), r.listOwners(animal.uuid()).getFirst().ref()); assertEquals(0, r.heldOwners());
            assertFalse(r.hasHeldOwner(animal.uuid())); assertEquals(1, r.listForCharacter(heir()).size());
            assertTrue(r.listForCharacter(new CharacterRef(account, UUID.randomUUID())).isEmpty());
            assertTrue(r.migrateOwners(() -> { fail("Completed mapping is immutable"); return false; }, id -> Optional.empty()));
            animal.setCare(59); r.upsertAnimal(animal);
            assertEquals("preserved", rows(connection(r), "SELECT future FROM husbandry_owner_archive").getFirst()[0]);
            assertEquals("animal-progress", rows(connection(r), "SELECT future FROM animals").getFirst()[0]);
            assertArrayEquals(new byte[]{1,2,3}, r.getSnapshot(animal.uuid()).orElseThrow().data());
            assertThrows(Exception.class, () -> update(connection(r), "DELETE FROM husbandry_owner_archive"));
            assertThrows(Exception.class, () -> update(connection(r), "UPDATE husbandry_owner_archive SET future = ''"));
            assertThrows(Exception.class, () -> update(connection(r), "INSERT INTO husbandry_owner_archive SELECT * FROM owners"));
            r.deleteAnimal(animal.uuid());
            assertEquals(1, scalar(connection(r), "SELECT COUNT(*) FROM husbandry_owner_archive"));
            assertEquals(0, scalar(connection(r), "SELECT COUNT(*) FROM owners"));
        }
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            assertTrue(repo.value.migrateOwners(() -> false, id -> Optional.empty()));
            assertEquals(1, scalar(connection(repo.value), "SELECT COUNT(*) FROM husbandry_owner_archive"));
        }
    }

    @Test void heldCoownerPreservesOtherSlotAndAccountCapacityDeduplicatesAnimals() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var r = repo.value; var animal = animal(r);
            legacy(r, animal.uuid(), account, "owner"); legacy(r, animal.uuid(), otherAccount, "coowner");
            r.migrateOwners(() -> true, id -> id.equals(account) ? Optional.of(heir()) : Optional.empty());
            assertEquals(1, r.listOwners(animal.uuid()).size()); assertTrue(r.hasHeldOwner(animal.uuid()));
            assertEquals(1, r.heldOwners()); assertEquals(1, r.countForPlayer(otherAccount));
            assertTrue(r.accountHasAnimal(otherAccount, animal.uuid()));
            r.upsertOwner(new HusbandryOwner(animal.uuid(), account, UUID.randomUUID(), "coowner"));
            assertEquals(1, r.countForPlayer(account)); assertEquals(1, r.listForPlayer(account).size());
            for (var owner : r.listOwners(animal.uuid())) r.deleteCharacterOwner(animal.uuid(), owner.characterUuid());
            assertTrue(r.hasAnyOwner(animal.uuid())); assertTrue(r.listOwners(animal.uuid()).isEmpty());
            assertFalse(r.accountHasAnimal(null, animal.uuid())); assertFalse(r.accountHasAnimal(account, null));
            assertFalse(r.hasAnyOwner(null)); assertFalse(r.hasHeldOwner(null)); assertTrue(r.listForCharacter(null).isEmpty());
        }
    }

    @Test void faultsBeforePublicationRollBackArchivePlanAndSlotsThenReplayConservesPayload() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var r = repo.value; var animal = animal(r); legacy(r, animal.uuid(), account, "owner");
            update(connection(r), "CREATE TRIGGER fail_publish BEFORE INSERT ON character_owners BEGIN SELECT RAISE(ABORT, 'fault'); END");
            assertThrows(SqliteDatabaseException.class, () -> r.migrateOwners(() -> true, id -> Optional.of(heir())));
            assertEquals(0, scalar(connection(r), "SELECT COUNT(*) FROM sqlite_master WHERE name = 'husbandry_owner_archive'"));
            assertEquals(0, scalar(connection(r), "SELECT COUNT(*) FROM husbandry_owner_plan"));
            assertEquals(0, scalar(connection(r), "SELECT COUNT(*) FROM character_owners"));
            assertEquals(47, r.getAnimal(animal.uuid()).orElseThrow().care());
            update(connection(r), "DROP TRIGGER fail_publish"); r.migrateOwners(() -> true, id -> Optional.of(heir()));
            assertEquals(1, r.listOwners(animal.uuid()).size());
            update(connection(r), "CREATE TRIGGER fail_owner BEFORE INSERT ON character_owners BEGIN SELECT RAISE(ABORT, 'fault'); END");
            var next = new HusbandryAnimal(UUID.randomUUID(), "SHEEP", "Not published");
            assertThrows(SqliteDatabaseException.class, () -> r.saveAnimalAndOwner(next,
                    new HusbandryOwner(next.uuid(), account, character, "owner")));
            assertFalse(r.exists(next.uuid()));
            assertThrows(SqliteDatabaseException.class, () -> r.upsertOwner(new HusbandryOwner(animal.uuid(), account, UUID.randomUUID(), "owner")));
        }
    }

    @Test void invalidIdentitiesRolesAndSlotCollisionsRemainHeldWithoutNormalization() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var r = repo.value; var unknown = animal(r); legacy(r, unknown.uuid(), account, "future-owner");
            var conflicting = animal(r); legacy(r, conflicting.uuid(), account, "owner");
            r.upsertOwner(new HusbandryOwner(conflicting.uuid(), otherAccount, character, "owner"));
            update(connection(r), "PRAGMA foreign_keys = OFF");
            update(connection(r), "INSERT INTO owners VALUES ('broken-animal', 'broken-account', 'owner')");
            UUID missing = UUID.randomUUID(); legacy(r, missing, account, "owner");
            update(connection(r), "PRAGMA foreign_keys = ON");
            r.migrateOwners(() -> true, id -> Optional.of(heir()));
            assertEquals(4, r.heldOwners()); assertTrue(r.hasAnyOwner(unknown.uuid()));
            assertEquals("future-owner", rows(connection(r), "SELECT role FROM husbandry_owner_archive WHERE animal_uuid = ?", unknown.uuid().toString()).getFirst()[0]);
            assertEquals(1, r.listOwners(conflicting.uuid()).size());
            assertThrows(IllegalArgumentException.class, () -> r.upsertOwner(new HusbandryOwner(unknown.uuid(), account, character, "strange")));
            assertThrows(IllegalArgumentException.class, () -> r.upsertOwner(new HusbandryOwner(conflicting.uuid(), account, character, "owner")));
            assertThrows(IllegalArgumentException.class, () -> r.saveAnimalAndOwner(conflicting, new HusbandryOwner(unknown.uuid(), account, character, "owner")));
            assertThrows(IllegalArgumentException.class, () -> r.saveAnimalAndOwner(conflicting, new HusbandryOwner(conflicting.uuid(), account, character, "owner")));
            update(connection(r), "INSERT INTO character_owners VALUES (?, 'bad', 'bad', 'owner')", unknown.uuid().toString());
            update(connection(r), "INSERT INTO character_owners VALUES (?, ?, ?, 'future')", unknown.uuid().toString(), account.toString(), character.toString());
            assertTrue(r.listOwners(unknown.uuid()).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> r.upsertOwner(new HusbandryOwner(unknown.uuid(), account, character, "owner")));
        }
    }

    @Test void recognizedCollisionKeepsStrongerRoleAndWrongAccountHeirRemainsHeld() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var r = repo.value; var first = animal(r); legacy(r, first.uuid(), account, "coowner");
            r.upsertOwner(new HusbandryOwner(first.uuid(), account, character, "owner"));
            var second = animal(r); legacy(r, second.uuid(), otherAccount, "owner");
            r.migrateOwners(() -> true, id -> Optional.of(heir()));
            assertEquals("owner", r.listOwners(first.uuid()).getFirst().role());
            assertEquals(1, r.heldOwners()); assertTrue(r.hasHeldOwner(second.uuid()));
            assertEquals("coowner", stronger("coowner", "coowner"));
            assertTrue(r.accountHasAnimal(account, first.uuid())); assertFalse(r.accountHasAnimal(account, second.uuid()));
        }
    }

    @Test void corruptedSchemaFailsClosedAndBindingFailureClosesStatement() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var r = repo.value; var animal = animal(r);
            update(connection(r), "DROP TABLE character_owners");
            assertThrows(SqliteDatabaseException.class, () -> r.listOwners(animal.uuid()));
            assertThrows(SqliteDatabaseException.class, () -> r.countForPlayer(account));
        }
        var db = org.mockito.Mockito.mock(net.tfminecraft.tlibs.database.SqliteDatabase.class);
        var connection = org.mockito.Mockito.mock(Connection.class);
        var statement = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        org.mockito.Mockito.when(db.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        org.mockito.Mockito.doThrow(new java.sql.SQLException("Binding interrupted")).when(statement).setObject(1, account.toString());
        var store = new HusbandryOwnershipStore(db);
        assertThrows(SqliteDatabaseException.class, () -> store.countAccount(account));
        org.mockito.Mockito.verify(statement).close();
    }

    @Test void archiveVerificationFailureRollsBackAndNeverPublishesRecipients() throws Exception {
        try (var repo = new Repository(directory.resolve("farm.db"))) {
            var real = repo.value.ownershipDatabase();
            var proxy = org.mockito.Mockito.spy(real.getConnection());
            var broken = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
            var count = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            org.mockito.Mockito.when(count.getInt(1)).thenReturn(-1);
            org.mockito.Mockito.when(broken.executeQuery()).thenReturn(count);
            org.mockito.Mockito.doReturn(broken).when(proxy).prepareStatement("SELECT COUNT(*) FROM husbandry_owner_archive");
            var db = org.mockito.Mockito.spy(real); org.mockito.Mockito.doReturn(proxy).when(db).getConnection();
            var store = new HusbandryOwnershipStore(db);
            assertThrows(SqliteDatabaseException.class, () -> store.migrate(() -> true, id -> Optional.empty()));
            assertEquals(0, scalar(real.getConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE name = 'husbandry_owner_archive'"));
        }
    }

    @Test void compatibilityAccountApisRemainPresentButCannotGrantOrDeleteCharacterRights() {
        try (var fixture = new Repository(directory.resolve("farm.db"))) {
            var repo = fixture.value; var animal = animal(repo);
            var legacy = new HusbandryOwner(animal.uuid(), account, "owner");
            assertNull(legacy.characterUuid()); assertEquals(account, legacy.playerUuid());
            assertThrows(IllegalArgumentException.class, () -> repo.upsertOwner(legacy));
            repo.upsertOwner(new HusbandryOwner(animal.uuid(), account, character, "owner"));
            assertFalse(HusbandryOwnershipService.isOwner(account, animal.uuid()));
            assertThrows(IllegalArgumentException.class, () -> repo.deleteOwner(animal.uuid(), account));
            assertEquals(1, repo.listOwners(animal.uuid()).size());
        }
    }

    @Test void finalResolvedSlotReleaseDoesNotLetRetainedLegacyRowsKeepAnimalsOwned() throws Exception {
        try(var fixture=new Repository(directory.resolve("farm.db"))) {
            var repo=fixture.value;var animal=animal(repo);legacy(repo,animal.uuid(),account,"owner");
            repo.migrateOwners(()->true,id->Optional.of(heir()));
            assertEquals(1,repo.listOwnedAnimals().size());
            repo.deleteCharacterOwner(animal.uuid(),character);
            assertFalse(repo.hasAnyOwner(animal.uuid()));assertTrue(repo.listOwnedAnimals().isEmpty());
            assertEquals(1,scalar(connection(repo),"SELECT COUNT(*) FROM owners"));
            var held=animal(repo);legacy(repo,held.uuid(),otherAccount,"coowner");
            repo.upsertOwner(new HusbandryOwner(held.uuid(),account,character,"owner"));
            repo.deleteCharacterOwner(held.uuid(),character);
            assertTrue(repo.hasAnyOwner(held.uuid()));assertEquals(1,repo.listOwnedAnimals().size());
        }
    }

    static class Repository implements AutoCloseable {
        final HusbandryRepository value;
        Repository(Path path) { value = HusbandryRepository.open(path.toFile()); }
        @Override public void close() { value.close(); }
    }
}
