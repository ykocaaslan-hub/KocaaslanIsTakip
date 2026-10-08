"""Read-only comparison of Kocaaslan v1/v2 backups. Never chooses/deletes rows."""
import argparse
from collections import defaultdict
from decimal import Decimal
import hashlib
import json
from pathlib import Path


def load_backup(path):
    raw = Path(path).read_bytes()
    data = json.loads(raw.decode('utf-8-sig'), parse_float=Decimal)
    if data.get('app') != 'Kocaaslan İş Takip' or data.get('version') not in (1, 2):
        raise ValueError('Unsupported backup format')
    if not isinstance(data.get('transactions'), list):
        raise ValueError('Missing transactions array')
    rows = []
    for index, item in enumerate(data['transactions'], 1):
        if not isinstance(item, dict):
            raise ValueError(f'Invalid row {index}')
        business, kind = item.get('business'), item.get('type')
        if not isinstance(business, str) or kind not in ('Gelir', 'Gider'):
            raise ValueError(f'Invalid business/type in row {index}')
        amount = Decimal(str(item.get('amount')))
        date = item.get('date')
        if not amount.is_finite() or amount <= 0 or isinstance(date, bool) or not isinstance(date, (int, Decimal)) or date <= 0 or date != int(date):
            raise ValueError(f'Invalid amount/date in row {index}')
        category, note = item.get('category') or '', item.get('note') or ''
        identity = item.get('syncId') or None
        if not isinstance(category, str) or not isinstance(note, str) or (identity is not None and not isinstance(identity, str)):
            raise ValueError(f'Invalid text/identity in row {index}')
        rows.append(dict(row=index, business=business, type=kind, amount=amount,
                         category=category, note=note, date=int(date), syncId=identity))
    return dict(file=Path(path).name, sha256=hashlib.sha256(raw).hexdigest(), rows=rows)


def content_key(row):
    return (row['business'], row['type'], row['amount'], row['category'], row['note'], row['date'])


def totals(rows):
    businesses = defaultdict(lambda: dict(rows=0, income=Decimal(0), expense=Decimal(0)))
    for row in rows:
        b = businesses[row['business']]
        b['rows'] += 1
        b['income' if row['type'] == 'Gelir' else 'expense'] += row['amount']
    return {name: dict(rows=b['rows'], income=str(b['income']), expense=str(b['expense']),
                       net=str(b['income'] - b['expense'])) for name, b in sorted(businesses.items())}


def compare(current, reference):
    now, before = defaultdict(list), defaultdict(list)
    for row in current['rows']:
        now[content_key(row)].append(row)
    for row in reference['rows']:
        before[content_key(row)].append(row)
    reference_ids = {r['syncId'] for r in reference['rows'] if r['syncId']}
    groups = []
    for key, rows in sorted(now.items()):
        original = before.get(key, [])
        if len(rows) <= 1 and not (original and len(rows) > len(original)):
            continue
        example = rows[0]
        groups.append(dict(
            content={k: str(example[k]) if k == 'amount' else example[k]
                     for k in ('business', 'type', 'amount', 'category', 'note', 'date')},
            current_count=len(rows), reference_count=len(original),
            additional_rows_compared_with_reference=max(0, len(rows)-len(original)) if original else None,
            current_rows=[dict(row=r['row'], syncId=r['syncId'],
                               identity_present_in_reference=bool(r['syncId'] and r['syncId'] in reference_ids)) for r in rows],
            reference_rows=[dict(row=r['row'], syncId=r['syncId']) for r in original]))
    return dict(
        read_only=True,
        note='Equal content does not prove duplication. Preserve legitimate repeated entries; review IDs and reference multiplicities before changing any data.',
        current={k: current[k] for k in ('file', 'sha256')},
        reference={k: reference[k] for k in ('file', 'sha256')},
        current_totals=totals(current['rows']), reference_totals=totals(reference['rows']),
        exact_double_by_content=bool(before) and set(now) == set(before) and all(len(now[k]) == 2*len(v) for k, v in before.items()),
        equal_content_groups=groups,
        deletion_candidates=[],
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    if args.output.resolve() in (args.current.resolve(), args.reference.resolve()):
        parser.error('Report output must not replace either input backup')
    report = compare(load_backup(args.current), load_backup(args.reference))
    # Exclusive creation also prevents accidentally replacing an earlier report.
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(f'Read-only report written: {args.output}')


if __name__ == '__main__':
    main()
