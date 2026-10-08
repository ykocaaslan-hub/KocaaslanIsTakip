import copy
from decimal import Decimal
import unittest
from prepare_reviewed_repair import prepare


def row(identity, archived=False, amount=5):
    return dict(syncId=identity, updatedAt=10, archived=archived, business='Yavuz Kocaaslan', type='Gider', amount=Decimal(amount), category='Kira', note='', date=1000)


class ReviewedRepairTests(unittest.TestCase):
    def setUp(self):
        self.reference = dict(rows=[row('original'), row('removed', True, 7)], sha256='reference',
                              diagnostics=dict(installationId='tablet', projectId='project', uid='user', pendingSyncIds=[]))
        self.current = copy.deepcopy(self.reference)
        self.current['sha256'] = 'phone'
        self.current['diagnostics']['installationId'] = 'phone'
        self.current['rows'] += [row('migration-a'), row('migration-b', False, 7)]

    def test_requires_explicit_reference_approval(self):
        with self.assertRaises(ValueError):
            prepare(self.current, self.reference)

    def test_exact_ids_include_archived_baseline_counterpart_without_mutating_backups(self):
        before = copy.deepcopy(self.current)
        result = prepare(self.current, self.reference, True)
        self.assertEqual(['migration-a', 'migration-b'], [r['syncId'] for r in result['targets']])
        self.assertEqual({'original', 'removed'}, {r['syncId'] for r in result['retained']})
        self.assertEqual('5', result['expected_active_totals']['Yavuz Kocaaslan']['expense'])
        self.assertEqual(self.current, before)

    def test_missing_changed_baseline_or_new_unmatched_record_is_preserved_by_refusal(self):
        variants = [copy.deepcopy(self.current) for _ in range(3)]
        variants[0]['rows'].pop(0)
        variants[1]['rows'][0]['updatedAt'] = 20
        variants[2]['rows'].append(row('new-unreviewed', False, 9))
        for current in variants:
            with self.assertRaises(ValueError):
                prepare(current, self.reference, True)

    def test_pending_wrong_account_or_duplicate_identity_is_rejected(self):
        variants = [copy.deepcopy(self.current) for _ in range(3)]
        variants[0]['diagnostics']['pendingSyncIds'] = ['migration-a']
        variants[1]['diagnostics']['uid'] = 'other'
        variants[2]['rows'].append(row('migration-a'))
        for current in variants:
            with self.assertRaises(ValueError):
                prepare(current, self.reference, True)


if __name__ == '__main__':
    unittest.main()
