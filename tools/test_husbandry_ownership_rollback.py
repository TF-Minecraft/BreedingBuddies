import importlib.util
import os
from pathlib import Path
import sqlite3
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('owner_rollback', Path(__file__).with_name('husbandry-ownership-rollback.py'))
rollback = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rollback)


class OwnershipRollbackTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.path = Path(self.temporary.name) / 'husbandry.db'
        self.database = sqlite3.connect(self.path)
        self.database.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE animals (uuid TEXT PRIMARY KEY, care INTEGER, snapshot BLOB);
            CREATE TABLE owners (animal_uuid TEXT, player_uuid TEXT, role TEXT, unknown TEXT,
                FOREIGN KEY(animal_uuid) REFERENCES animals(uuid));
            INSERT INTO animals VALUES ('animal', 71, X'0001FF');
            INSERT INTO owners VALUES ('animal','account','owner',NULL);
            CREATE TABLE husbandry_owner_archive AS SELECT * FROM owners;
            INSERT INTO husbandry_owner_archive VALUES ('deleted-animal','account','coowner','original');
            CREATE TABLE character_owners (animal_uuid TEXT, account_uuid TEXT, character_uuid TEXT, role TEXT);
            INSERT INTO character_owners VALUES ('animal','heir-account','heir','owner');
            CREATE TABLE husbandry_owner_plan (animal_uuid TEXT, account_uuid TEXT, character_uuid TEXT, role TEXT, status TEXT);
            INSERT INTO husbandry_owner_plan VALUES ('animal','account','original-character','owner','resolved');
            INSERT INTO husbandry_owner_plan VALUES ('deleted-animal','account',NULL,'coowner','held');
        ''')
        self.database.commit()

    def tearDown(self):
        self.database.close()
        self.temporary.cleanup()

    def test_preview_retains_every_current_row(self):
        result = rollback.rollback(self.path)
        self.assertEqual(2, result['archived_slots'])
        self.assertEqual(1, result['restorable_slots'])
        self.assertEqual(1, result['changed_resolved_slots'])
        self.assertFalse(result['applied'])
        self.assertEqual(('heir-account',), self.database.execute('SELECT account_uuid FROM character_owners').fetchone())
        self.assertEqual(71, self.database.execute('SELECT care FROM animals').fetchone()[0])

    def test_apply_preserves_verified_wal_backup_progress_and_null_fields(self):
        backup = self.path.with_name('recover.db')
        result = rollback.rollback(self.path, True, backup)
        self.assertTrue(result['applied'])
        self.assertEqual([('animal','account','owner',None)], self.database.execute('SELECT * FROM owners').fetchall())
        self.assertEqual((71,b'\x00\x01\xff'), self.database.execute('SELECT care,snapshot FROM animals').fetchone())
        with sqlite3.connect(backup) as recovered:
            self.assertEqual(('heir-account',), recovered.execute('SELECT account_uuid FROM character_owners').fetchone())
            self.assertEqual(('ok',), recovered.execute('PRAGMA integrity_check').fetchone())
        with self.assertRaises(FileExistsError):
            rollback.rollback(self.path, True, backup)
        self.assertEqual(1, self.database.execute('SELECT COUNT(*) FROM animals').fetchone()[0])

    def test_missing_archive_and_changed_legacy_schema_are_refused(self):
        self.database.execute('ALTER TABLE owners DROP COLUMN unknown')
        self.database.commit()
        with self.assertRaisesRegex(ValueError, 'schema conflicts'):
            rollback.rollback(self.path)
        self.database.execute('DROP TABLE husbandry_owner_archive')
        self.database.commit()
        with self.assertRaisesRegex(ValueError, 'No immutable'):
            rollback.rollback(self.path)

    def test_backup_stays_private_with_permissive_umask_and_never_changes_existing_modes(self):
        self.path.chmod(0o600)
        self.database.execute('UPDATE animals SET care=93')
        self.database.commit()  # Committed WAL content must reach the restricted recovery image.
        backup = self.path.with_name('private-recovery.db')
        previous = os.umask(0)
        try:
            result = rollback.rollback(self.path, True, backup)
        finally:
            os.umask(previous)
        self.assertTrue(result['applied'])
        self.assertEqual(0o600, backup.stat().st_mode & 0o777)
        self.assertEqual(0o600, self.path.stat().st_mode & 0o777)
        with sqlite3.connect(backup) as recovered:
            self.assertEqual((93, b'\x00\x01\xff'), recovered.execute('SELECT care,snapshot FROM animals').fetchone())
            self.assertEqual(('ok',), recovered.execute('PRAGMA integrity_check').fetchone())
        existing = self.path.with_name('existing.db')
        existing.write_bytes(b'original recovery image')
        existing.chmod(0o640)
        with self.assertRaises(FileExistsError):
            rollback.rollback(self.path, True, existing)
        self.assertEqual(b'original recovery image', existing.read_bytes())
        self.assertEqual(0o640, existing.stat().st_mode & 0o777)
        self.assertEqual(0o600, self.path.stat().st_mode & 0o777)


if __name__ == '__main__':
    unittest.main()
