"""Prepare a reversible, explicit-ID archive plan after approval of a device baseline."""
import argparse
import json
from pathlib import Path
from compare_backups import load_backup, content_key, totals


def prepare(current, reference, approved=False):
    if not approved:
        raise ValueError('Reference device must be explicitly approved')
    a = {r['syncId']: r for r in current['rows']}
    b = {r['syncId']: r for r in reference['rows']}
    if None in a or None in b or len(a) != len(current['rows']) or len(b) != len(reference['rows']):
        raise ValueError('Unique existing IDs required')
    for identity, row in b.items():
        other = a.get(identity)
        if other is None or (content_key(row), row['archived'], row['updatedAt']) != (content_key(other), other['archived'], other['updatedAt']):
            raise ValueError('Shared baseline record missing or changed; review devices first')
    cd, rd = current['diagnostics'], reference['diagnostics']
    if not cd.get('installationId') or not rd.get('installationId') or cd['installationId'] == rd['installationId']:
        raise ValueError('Two distinct diagnosed installations required')
    for key in ('projectId', 'uid'):
        if not cd.get(key) or cd[key] != rd.get(key):
            raise ValueError('Firebase project/account must match')
    if cd.get('pendingSyncIds') or rd.get('pendingSyncIds'):
        raise ValueError('Pending uploads must be reviewed before preparing a plan')
    targets, retained, pairs = [], {}, []
    for identity in sorted(a.keys() - b.keys()):
        row = a[identity]
        if row['archived']:
            continue
        candidates = sorted((r for r in b.values() if content_key(r) == content_key(row)), key=lambda r: (r['archived'], r['syncId']))
        if not candidates:
            raise ValueError('Extra record has no reviewed reference counterpart; preserve it')
        counterpart = candidates[0]
        targets.append(row)
        retained[counterpart['syncId']] = counterpart
        pairs.append(dict(target=identity, retained=counterpart['syncId']))
    if not targets:
        raise ValueError('No reviewed extra active IDs')
    def snapshot(row):
        return {key: float(row[key]) if key == 'amount' else row[key] for key in
                ('business', 'type', 'amount', 'category', 'note', 'date', 'syncId', 'updatedAt', 'archived')}
    result = dict(app='Kocaaslan İş Takip İncelenmiş Düzeltme', version=1,
                  installationId=cd['installationId'], projectId=cd['projectId'], uid=cd['uid'],
                  source_sha256=current['sha256'], reference_sha256=reference['sha256'],
                  targets=[snapshot(r) for r in targets], retained=[snapshot(retained[i]) for i in sorted(retained)], pairs=pairs,
                  expected_active_totals=totals([r for r in current['rows'] if r['syncId'] not in {t['syncId'] for t in targets}]))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--approve-reference', action='store_true', required=True)
    args = parser.parse_args()
    if args.output.resolve() in (args.current.resolve(), args.reference.resolve()):
        parser.error('Output cannot replace input backups')
    result = prepare(load_backup(args.current), load_backup(args.reference), args.approve_reference)
    with args.output.open('x', encoding='utf-8') as out:
        json.dump(result, out, ensure_ascii=False, indent=2)
        out.write('\n')
    print(f'Reviewed explicit-ID plan written: {len(result["targets"])} targets; inputs unchanged')


if __name__ == '__main__':
    main()
