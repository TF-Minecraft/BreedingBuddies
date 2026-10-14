import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys

spec = importlib.util.spec_from_file_location('rollback', Path(__file__).with_name('animal-ownership-rollback.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RollbackTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / 'Data'
        for folder in ['ownership/journals', 'PlayerData', 'CharacterData']:
            (self.root / folder).mkdir(parents=True, exist_ok=True)
        self.row = {'uuid': 'animal', 'future': {'kept': None}, 'characterOwners': [{'account': 'account', 'character': 'character'}]}
        self.legacy = json.dumps({'animals': [{'uuid': 'animal', 'future': None, 'ownersUuids': ['account']}]})
        (self.root / 'PlayerData/account.json').write_text(self.legacy)
        (self.root / 'ownership/source.json').write_text(json.dumps({'PlayerData/account.json': self.legacy}))
        (self.root / 'ownership/migration.json').write_text(json.dumps({'canonical': {'animal': self.row}, 'held': {}}))
        (self.root / 'CharacterData/character.json').write_text(json.dumps({'animals': [self.row]}))
        (self.root / 'character-unowned.json').write_text(json.dumps({'animals': []}))

    def test_preview_is_read_only_apply_preserves_every_byte_and_refuses_backup_overwrite(self):
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual([], module.rollback(self.root)['conflicts'])
        self.assertEqual(before, {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()})
        backup = Path(self.temporary.name) / 'recovery'
        self.assertTrue(module.rollback(self.root, True, backup)['applied'])
        self.assertEqual(before, {p.relative_to(backup / 'data'): p.read_bytes() for p in (backup / 'data').rglob('*') if p.is_file()})
        with self.assertRaises(FileExistsError):
            module.rollback(self.root, True, backup)
        with self.assertRaises(ValueError):
            module.rollback(self.root, True)
        with self.assertRaises(ValueError):
            module.rollback(self.root, True, self.root / 'backup')

    def test_progress_death_grants_and_conflicting_fanout_refuse_apply_without_rewriting(self):
        changed = dict(self.row, fed=True)
        (self.root / 'CharacterData/character.json').write_text(json.dumps({'animals': [changed]}))
        (self.root / 'CharacterData/other.json').write_text(json.dumps({'animals': [self.row]}))
        result = module.rollback(self.root)
        self.assertIn('conflicting fanout: animal', result['conflicts'])
        self.assertEqual(['animal'], result['changed_animals'])
        with self.assertRaises(ValueError):
            module.rollback(self.root, True, Path(self.temporary.name) / 'refused')
        self.assertTrue(json.loads((self.root / 'CharacterData/character.json').read_text())['animals'][0]['fed'])
        self.assertFalse((Path(self.temporary.name) / 'refused').exists())
        (self.root / 'CharacterData/other.json').unlink()
        (self.root / 'CharacterData/character.json').write_text(json.dumps({'animals': []}))
        self.assertEqual(['animal'], module.rollback(self.root)['changed_animals'])

    def test_utf8_crlf_sources_compare_and_restore_as_exact_bytes_without_resetting_evidence(self):
        text = '{\r\n  "animals": [], "name": "Éléphant 水", "future": null\r\n}\r\n'
        source = self.root / 'PlayerData/account.json'
        source.write_bytes(text.encode('utf-8'))
        (self.root / 'ownership/source.json').write_text(json.dumps({'PlayerData/account.json': text}, ensure_ascii=False), encoding='utf-8')
        (self.root / 'ownership/journals/estate.json').write_text('{"done": true, "estate": {"kept": null}}', encoding='utf-8')
        (self.root / 'ownership/token-effects').mkdir()
        (self.root / 'ownership/token-effects/completed.json').write_text('{"status": "done", "ownerJournal": "estate"}', encoding='utf-8')
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual([], module.rollback(self.root)['conflicts'])
        result = module.rollback(self.root, True, Path(self.temporary.name) / 'utf8-recovery')
        self.assertTrue(result['applied'])
        self.assertEqual(before, {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()})
        self.assertEqual(before, {p.relative_to(Path(result['backup']) / 'data'): p.read_bytes() for p in (Path(result['backup']) / 'data').rglob('*') if p.is_file()})

    def test_json_simple_escapes_raw_utf8_and_cli_name_the_actual_rollback_contract(self):
        text = '{\r\n "animals": [], "path": "stable/farm", "name": "Éléphant 水 €", "future": null\r\n}\r\n'
        (self.root / 'PlayerData/account.json').write_bytes(text.encode('utf-8'))
        archive = json.dumps({'PlayerData/account.json': text}, ensure_ascii=False).replace('/', '\\/').replace('€', '\\u20ac')
        (self.root / 'ownership/source.json').write_bytes(archive.encode('utf-8'))
        self.assertIn(b'\\r\\n', archive.encode('utf-8'))
        self.assertIn(b'\\/', archive.encode('utf-8'))
        self.assertIn(b'\\u20ac', archive.encode('utf-8'))
        self.assertIn('水'.encode('utf-8'), archive.encode('utf-8'))
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual([], module.rollback(self.root)['conflicts'])
        result = json.loads(subprocess.check_output([sys.executable, str(Path(module.__file__)), str(self.root), '--apply', '--backup', str(Path(self.temporary.name) / 'escaped-recovery')], text=True))
        self.assertTrue(result['applied'])
        self.assertIn('d6fa922', result['target'])
        self.assertIn('no published JAR', result['target'])
        self.assertIn('does not migrate again', result['character_restart'])
        self.assertIn('refuses to open until staff review', result['after_legacy_writes'])
        self.assertEqual(['character data', 'journals', 'receipts'], result['preserved'])
        self.assertEqual(before, {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()})
        self.assertEqual(before, {p.relative_to(Path(result['backup']) / 'data'): p.read_bytes() for p in (Path(result['backup']) / 'data').rglob('*') if p.is_file()})
        help_text = ' '.join(subprocess.check_output([sys.executable, str(Path(module.__file__)), '--help'], text=True).split())
        self.assertIn('pre-character', help_text)
        self.assertIn('d6fa922', help_text)
        self.assertIn('does not migrate again', help_text)
        self.assertIn('staff review', help_text)

    def test_nested_duplicate_keys_refuse_preview_and_apply_without_writing(self):
        path = self.root / 'CharacterData/character.json'
        path.write_text('{"animals": [{"uuid": "animal", "future": {"kept": null, "kept": 1}}]}')
        original = path.read_bytes()
        for apply in [False, True]:
            with self.assertRaises(ValueError):
                module.rollback(self.root, apply, Path(self.temporary.name) / 'refused')
            self.assertEqual(original, path.read_bytes())
        self.assertFalse((Path(self.temporary.name) / 'refused').exists())

    def test_pending_journal_and_legacy_changes_refuse_and_unknown_source_paths_are_rejected(self):
        (self.root / 'ownership/journals/pending.json').write_text(json.dumps({'done': False}))
        (self.root / 'PlayerData/account.json').write_text('{}')
        (self.root / 'PlayerData/new.json').write_text('{}')
        result = module.rollback(self.root)
        self.assertEqual(3, len(result['conflicts']))
        with self.assertRaises(ValueError):
            module.rollback(self.root, True, Path(self.temporary.name) / 'refused')
        (self.root / 'ownership/source.json').write_text(json.dumps({'../outside': '{}'}))
        with self.assertRaises(ValueError):
            module.rollback(self.root)
