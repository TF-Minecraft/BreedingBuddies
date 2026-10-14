#!/usr/bin/env python3
"""Prepare legacy account files for an old-JAR rollback on a stopped server; retain character state."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil


def _unique_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError('duplicate JSON key: ' + key)
        value[key] = item
    return value


def parse_document(text):
    return json.loads(text, object_pairs_hook=_unique_object)


def preview(root):
    root = Path(root).resolve()
    archive = parse_document((root / 'ownership/source.json').read_text(encoding='utf-8'))
    plan = parse_document((root / 'ownership/migration.json').read_text(encoding='utf-8'))
    conflicts = []
    legacy = {str(p.relative_to(root)) for p in (root / 'PlayerData').glob('*.json')}
    if (root / 'unownedanimals.json').exists():
        legacy.add('unownedanimals.json')
    if legacy != set(archive):
        conflicts.append('legacy file inventory changed')
    for name, text in archive.items():
        path = root / name
        if path.is_absolute() and not path.resolve().is_relative_to(root):
            raise ValueError('unsafe archive path')
        if not path.exists() or path.read_bytes() != text.encode('utf-8'):
            conflicts.append('legacy source changed: ' + name)
    for path in (root / 'ownership/journals').glob('*.json'):
        if not parse_document(path.read_text(encoding='utf-8')).get('done'):
            conflicts.append('incomplete journal: ' + path.name)
    for path in (root / 'ownership/token-effects').glob('*.json'):
        if parse_document(path.read_text(encoding='utf-8')).get('status') != 'done':
            conflicts.append('unfinished token effect: ' + path.name)
    copies = {}
    differing = set()
    character_files = list((root / 'CharacterData').glob('*.json'))
    unowned = root / 'character-unowned.json'
    if unowned.exists():
        character_files.append(unowned)
    for path in character_files:
        document = parse_document(path.read_text(encoding='utf-8'))
        for row in document['animals']:
            previous = copies.setdefault(row['uuid'], row)
            if previous != row:
                conflicts.append('conflicting fanout: ' + row['uuid'])
            if plan['canonical'].get(row['uuid']) != row:
                differing.add(row['uuid'])
    canonical = plan['canonical']
    changed = sorted(differing | (set(canonical) ^ set(copies)))
    conflicts.extend('post-migration animal changes: ' + key for key in changed)
    return {'archived_files': len(archive), 'canonical_animals': len(canonical), 'current_animals': len(copies),
            'held_animals': len(plan['held']), 'changed_animals': changed, 'conflicts': sorted(set(conflicts)), 'applied': False}


def rollback(root, apply=False, backup=None):
    root = Path(root).resolve()
    result = preview(root)
    if not apply:
        return result
    if result['conflicts']:
        raise ValueError('Manual reconciliation required: ' + '; '.join(result['conflicts']))
    if backup is None:
        raise ValueError('Choose a new backup directory with --backup')
    target = Path(backup).resolve()
    if target.is_relative_to(root) or root.is_relative_to(target):
        raise ValueError('Recovery directory must be separate from plugin data')
    target.mkdir(parents=True, exist_ok=False)
    originals = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
    shutil.copytree(root, target / 'data')
    copied = {str(p.relative_to(target / 'data')): hashlib.sha256(p.read_bytes()).hexdigest() for p in (target / 'data').rglob('*') if p.is_file()}
    if copied != originals:
        raise ValueError('Recovery image verification failed')
    (target / 'SHA256.json').write_text(json.dumps(originals, indent=2), encoding='utf-8')
    # Force every copied file and directory before touching legacy authority.
    for path in sorted(target.rglob('*'), key=lambda p: len(p.parts), reverse=True):
        descriptor = os.open(path, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    archive = parse_document((root / 'ownership/source.json').read_text(encoding='utf-8'))
    for name, text in archive.items():
        path = root / name
        temporary = path.with_name(path.name + '.rollback-next')
        with temporary.open('xb') as handle:
            handle.write(text.encode('utf-8'))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        descriptor = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    result.update(applied=True, backup=str(target))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('data', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--backup', type=Path)
    arguments = parser.parse_args()
    print(json.dumps(rollback(arguments.data, arguments.apply, arguments.backup), indent=2))


if __name__ == '__main__':
    main()
