#!/usr/bin/env python3
"""Preview an offline ownership rollback; --apply preserves a consistent current SQLite backup first."""
import argparse
import json
import os
from pathlib import Path
import sqlite3
import time


def rollback(path, apply=False, backup=None):
    path = Path(path).resolve()
    uri = path.as_uri() + ('?mode=rw' if apply else '?mode=ro')
    with sqlite3.connect(uri, uri=True) as database:
        database.execute('PRAGMA foreign_keys=ON')
        if database.execute('PRAGMA integrity_check').fetchone() != ('ok',):
            raise ValueError('SQLite integrity check failed')
        columns = [row[1] for row in database.execute('PRAGMA table_info(husbandry_owner_archive)')]
        if not columns:
            raise ValueError('No immutable husbandry owner archive exists')
        current_columns = {row[1] for row in database.execute('PRAGMA table_info(owners)')}
        if not set(columns) <= current_columns:
            raise ValueError('Legacy owner schema conflicts with archive; review by hand')
        result = {
            'archived_slots': database.execute('SELECT COUNT(*) FROM husbandry_owner_archive').fetchone()[0],
            'restorable_slots': database.execute('SELECT COUNT(*) FROM husbandry_owner_archive o JOIN animals a ON a.uuid=o.animal_uuid').fetchone()[0],
            'current_character_slots': database.execute('SELECT COUNT(*) FROM character_owners').fetchone()[0],
            'held_legacy_slots': database.execute("SELECT COUNT(*) FROM husbandry_owner_plan WHERE status='held'").fetchone()[0],
            'changed_resolved_slots': database.execute('''SELECT COUNT(*) FROM husbandry_owner_plan p
                WHERE p.status='resolved' AND NOT EXISTS (SELECT 1 FROM character_owners c
                WHERE c.animal_uuid=p.animal_uuid AND c.account_uuid=p.account_uuid
                AND c.character_uuid=p.character_uuid AND c.role=p.role)''').fetchone()[0],
            'applied': False,
        }
        if not apply:
            return result
        target = Path(backup) if backup else path.with_name(path.name + '.before-owner-rollback-' + str(time.time_ns()))
        # Never overwrite the only recovery image. SQLite backup includes committed WAL pages.
        descriptor = os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        os.close(descriptor)
        with sqlite3.connect(target) as copy:
            database.backup(copy)
            if copy.execute('PRAGMA integrity_check').fetchone() != ('ok',):
                raise ValueError('Current-state backup verification failed')
        quoted = ', '.join('"' + column.replace('"', '""') + '"' for column in columns)
        selected = ', '.join('o."' + column.replace('"', '""') + '"' for column in columns)
        database.execute('BEGIN IMMEDIATE')
        database.execute('DELETE FROM owners')
        database.execute(f'INSERT INTO owners ({quoted}) SELECT {selected} FROM husbandry_owner_archive o JOIN animals a ON a.uuid=o.animal_uuid')
        database.commit()
        result.update(applied=True, backup=str(target))
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('database', type=Path)
    parser.add_argument('--apply', action='store_true', help='Restore account owner slots for old Cooking while the server is stopped')
    parser.add_argument('--backup', type=Path, help='New path for the current-state SQLite recovery image')
    arguments = parser.parse_args()
    print(json.dumps(rollback(arguments.database, arguments.apply, arguments.backup), indent=2))


if __name__ == '__main__':
    main()
