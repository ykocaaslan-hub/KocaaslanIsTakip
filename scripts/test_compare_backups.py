import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from compare_backups import compare, load_backup, main


def row(identity, amount=5, note='same'):
    value = dict(business='shop', type='Gider', amount=amount, category='Kira', note=note, date=1000)
    if identity is not None:
        value['syncId'] = identity
    return value


class ComparisonTest(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.addCleanup(self.tmp.cleanup)

    def backup(self, name, rows, version=2):
        path = self.root/name
        path.write_text(json.dumps(dict(app='Kocaaslan İş Takip', version=version, transactions=rows)), encoding='utf-8')
        return path

    def test_doubled_legacy_backup_is_reported_without_selecting_or_deleting_rows(self):
        original = self.backup('original.json', [row(None)], 1)
        current = self.backup('current.json', [row('cloud'), row('legacy')])
        original_bytes, current_bytes = original.read_bytes(), current.read_bytes()
        output = self.root/'report.json'
        main(['--current', str(current), '--reference', str(original), '--output', str(output)])
        result = json.loads(output.read_text())
        self.assertTrue(result['exact_double_by_content'])
        self.assertEqual(result['current_totals']['shop']['expense'], '10')
        self.assertEqual(result['reference_totals']['shop']['expense'], '5')
        group = result['equal_content_groups'][0]
        self.assertEqual(group['additional_rows_compared_with_reference'], 1)
        self.assertEqual([r['syncId'] for r in group['current_rows']], ['cloud', 'legacy'])
        self.assertEqual(result['deletion_candidates'], [])
        self.assertEqual(current.read_bytes(), current_bytes)
        self.assertEqual(original.read_bytes(), original_bytes)

    def test_two_legitimate_identical_reference_entries_remain_two(self):
        reference = self.backup('original.json', [row('a'), row('b')])
        current = self.backup('current.json', [row('a'), row('b')])
        result = compare(load_backup(current), load_backup(reference))
        self.assertFalse(result['exact_double_by_content'])
        self.assertEqual(result['equal_content_groups'][0]['additional_rows_compared_with_reference'], 0)
        self.assertEqual(result['equal_content_groups'][0]['reference_count'], 2)
        self.assertEqual(result['current_totals']['shop']['expense'], '10')

    def test_reference_multiplicity_is_preserved_in_doubling_report(self):
        reference = self.backup('original.json', [row(None), row(None)], 1)
        current = self.backup('current.json', [row('a'), row('b'), row('c'), row('d')])
        result = compare(load_backup(current), load_backup(reference))
        self.assertTrue(result['exact_double_by_content'])
        self.assertEqual(result['equal_content_groups'][0]['additional_rows_compared_with_reference'], 2)

    def test_different_note_is_not_grouped_and_money_uses_decimal_arithmetic(self):
        reference = self.backup('original.json', [row('a', 0.1), row('b', 0.2, 'different')])
        current = self.backup('current.json', [row('a', 0.1), row('b', 0.2, 'different')])
        result = compare(load_backup(current), load_backup(reference))
        self.assertEqual(result['equal_content_groups'], [])
        self.assertEqual(result['current_totals']['shop']['expense'], '0.3')

    def test_legacy_import_id_matches_java_oracle_and_identifies_new_import(self):
        original = self.root/'original.json'
        original.write_text('{"app":"Kocaaslan İş Takip","version":1,"transactions":[{"business":"shop","type":"Gider","amount":5,"category":"Kira","note":"same","date":1000}]}', encoding='utf-8')
        # Verified with Java's UUID.nameUUIDFromBytes on the exact fixture file and "#0".
        generated = 'efc1821f-864b-3cf8-9a34-04a6ec3adc4e'
        current = self.backup('current.json', [row('original-cloud-id'), row(generated)])
        result = compare(load_backup(current), load_backup(original))
        flags = [r['matches_app_legacy_import_id'] for r in result['equal_content_groups'][0]['current_rows']]
        self.assertEqual(flags, [False, True])
        self.assertEqual(result['deletion_candidates'], [])

    def test_v3_archive_is_preserved_in_report_and_excluded_from_active_totals(self):
        original = self.backup('original.json', [row(None)], 1)
        active, archived = row('original'), row('import')
        active['archived'], archived['archived'] = False, True
        current = self.backup('current.json', [active, archived], 3)
        result = compare(load_backup(current), load_backup(original))
        self.assertEqual(result['current_totals']['shop']['expense'], '5')
        self.assertEqual(result['current_archived_rows'], 1)
        self.assertEqual([r['archived'] for r in result['equal_content_groups'][0]['current_rows']], [False, True])
        self.assertEqual(len(load_backup(current)['rows']), 2)

    def test_v3_rejects_missing_identity_or_invalid_archive_flag(self):
        for item in [row(None), row('a'), dict(row('a'), archived='false')]:
            current = self.backup('invalid.json', [item], 3)
            with self.assertRaises(ValueError):
                load_backup(current)

    def test_inputs_cannot_be_overwritten(self):
        original = self.backup('original.json', [row(None)], 1)
        current = self.backup('current.json', [row('a')])
        before = current.read_bytes()
        with self.assertRaises(SystemExit):
            main(['--current', str(current), '--reference', str(original), '--output', str(current)])
        self.assertEqual(current.read_bytes(), before)


if __name__ == '__main__':
    unittest.main()
