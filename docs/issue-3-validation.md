# Issue #3 validation

The app retains `kocaaslan_is_takip.db` and upgrades it in place to schema 4.
No record is removed or combined because its content resembles another record.
Missing/duplicate sync IDs are repaired without deleting rows. Existing IDs remain
unchanged wherever possible. Existing content duplicates are intentionally retained.

New backups include syncId and updatedAt. Restores merge absent IDs and retain current
rows. Legacy v1 files derive IDs from the file bytes and row position, so importing
the exact same legacy file twice is repeatable without collapsing separate rows.
Changing the bytes of a legacy backup can produce different IDs; there is no reliable
way to infer the original identity from matching transaction contents.

SQLite sync_state and deleted_transactions form the durable outbox. Firestore uses
its default persistent cache. Pending local edits ignore cloud snapshots until a
server transaction acknowledges their exact version. A later local edit stays queued.
Uploads check the remote version inside a Firestore transaction: the higher updatedAt
wins; existing explicit deletion tombstones prevent resurrection. A user deletion
creates a tombstone and retries after reconnect. Missing documents in a snapshot
never delete local rows. Firestore transactions need an internet connection; offline
failure keeps SQLite's queue for retry every 10 seconds and on activity resume.

Diagnosis uses the existing Firestore client, exposes project/UID and Auth/Firestore
errors, and never terminates a live listener. Sessions capture the UID and stop on
activity stop, destruction, and sign-out. Diagnosis timeout only ends its UI callback;
it does not clear the cache or the upload queue.

## Automated checks

From the repository root:

```sh
cp -R sync-src app
python3 scripts/patch145.py
cd app
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The APK workflow runs these checks on pull requests and the issue branch. Push builds
also assemble release with the repository's KOCAASLAN_SIGNING_PASSWORD secret and
existing release keystore, verify the signature and versionName=1.6.7/versionCode=127,
and upload the APK and test reports. Passwords and key material are never printed.
The sandbox used for this change has Java but no Gradle/Android SDK; Actions is the
Android build/test environment. The legacy patch script now checks the source layout
rather than rewriting it at build time.

## Required real-device verification (not performed by the coding agent)

1. Export backups from both devices before installing the signed update over the
   existing app. Keep the app installed and its database intact.
2. Confirm diagnosis shows the same Firebase project and UID on both devices.
   Record the precise Auth/Firestore error if access fails; check that the existing
   rules allow reading and writing the user's transactions (transactions also need
   reads). This change does not modify Firebase rules or existing cloud records.
3. Add two separate entries with identical date, amount and note on phone. Confirm
   both arrive on tablet once, edit one on tablet, and confirm phone receives that
   update. Restart both apps several times; record counts should stay unchanged.
4. Turn off networking, add/edit on each device, restart while offline, then restore
   networking. Confirm pending edits arrive once and survive restart. Test an explicit
   user deletion using a disposable test entry; it should not reappear after restart.
5. Run diagnosis while sync is active; make another edit afterwards and check it still
   arrives. Background/resume each app and verify listener/retry recovery.
6. Compare daily/weekly/monthly/all income, expense and net with the selected list,
   including Monday, month boundaries, search and both businesses. On phone/tablet
   and portrait/landscape, scroll the last row and use all four bottom navigation items.
7. Back up and restore the same v2 file twice. Current rows and their IDs must survive;
   record count should not increase on the second restore. Keep pre-existing legitimate
   same-content entries. Do not remove old duplicates automatically.

Two-device/live-project verification is still necessary to establish that the original
phone/tablet problem is resolved. Automated tests use local SQLite and mocked Firebase;
they do not validate live credentials, rules, network or concurrent device clocks.

## Restore visibility follow-up

Restoring an old backup previously left the current date filter, search and business
selection intact. Historical rows could therefore be absent from the list while the
all-date totals contained amounts. Restore now opens All, clears search, selects a
business present in the backup, and shows a persistent report: newly inserted IDs,
existing IDs retained, explicit deletion tombstones retained, and each business's
current record count/income/expense/net. The confirmation shows the backup's own
counts/totals separately from the merged device totals. A filtered empty list states
how many rows are stored and offers Show all records. General totals label the
business and record count. Errors reading/parsing/importing a backup remain visible
in a dialog; a network failure after a successful local import does not report the
import as failed.

Additional regression cases use historical v1 backups, an active search, another
business, repeated import, recreation, retained local rows and deletion tombstones,
a transaction rollback on invalid input, and Firebase unavailable after import.
These tests validate visibility and calculations for fixture data. Provided user
backups were subsequently compared locally; their contents are not committed.

## Identity-less legacy backup overlap

A v1 backup has no original sync IDs. The deterministic file/position IDs prevent
importing the exact same file twice, but cannot identify the same original rows
already stored under cloud IDs. Importing that backup into an existing account can
therefore create a second set. Restores containing missing IDs now require a second,
explicit confirmation when local rows or a signed-in account exist (or auth state is
unknown). Cancel and Back up existing records perform no import or cloud write.
Adding as separate transactions remains available for genuinely different entries.
Existing same-content records are never automatically merged, excluded or deleted.

To assess existing doubled data, export a current v2 backup with the app's Back up
button and retain the pre-import backup. Compare them offline:

```sh
python3 scripts/compare_backups.py --current current.json --reference original.json --output comparison.json
```

This creates a new JSON report exclusively; input files cannot be overwritten.
It reports business totals with Decimal arithmetic, groups of equal content, source
row numbers and sync IDs, exact legacy-import ID matches using the app's file/position
UUID algorithm, reference multiplicity and whether the entire current
content distribution is twice the reference distribution. Equal content is a review
signal, not proof that an entry should be deleted. Two legitimate identical entries
in the original remain two in the baseline; the tool never selects a deletion or
writes to SQLite/Firestore. It does not establish which of two different IDs is the
original when the reference has no IDs. Actual user backups and explicit ID-based
review are required before repairing existing duplicated data.


## Reversible legacy-import repair

`Çift kayıtları düzelt` opens a separate document picker for the exact, pre-import
legacy file. This action never imports that file. `BackupData` reproduces the existing
UUID algorithm from its UTF-8 text and zero-based row position. The repair accepts
only a source with no original identities; it must find those exact import IDs in the
current database, with unchanged content and enough active, non-import counterpart
IDs to preserve the source's multiplicity. Equal content without matching import IDs
is never sufficient. New transactions, edited copies, missing originals and genuine
repeated source transactions are preserved. Changed file bytes give different IDs and
produce no matching targets. This is explicit reversal of a reviewed import, never a
startup content-deduplication routine.

The preview gives archive/skip counts and both businesses' resulting active totals.
Cancel and exporting a backup do not mutate data. Positive confirmation saves a full
v3 pre-operation snapshot in app-private storage, then atomically revalidates target
and retained-counterpart identities, content and versions. A changed preview aborts
the whole operation. The SQLite v4 migration adds `archived` with a default of false;
archive toggles this field with a new version and a pending upload. No transaction is
deleted or moved out of its existing database row, and no deletion tombstone is made.

Active lists, income/expense/net, period totals, categories, charts and CSV exclude
archives. `all()` and the outbox include them. Firestore documents retain the entire
payload and receive `archived: true/false`; the same existing version checks apply.
A newer archive defeats an older upload/cached snapshot. Offline archive/undo stays
in SQLite across restarts. `Ayarlar → Arşivdeki kayıtlar → Arşivi geri al` explicitly
reactivates archived entries with a newer version and queues that change for other
devices. It warns that copies will increase totals again. Archives are recoverable
without depending on a Firestore deletion or reinstating a new identity.

New backup schema 3 includes **every** active and archived record, original sync IDs,
versions and boolean archive state. Restoring into an empty database preserves this
state; merging into an existing database still leaves its newer/current rows intact.
A v3 backup without an identity or a boolean archive flag is rejected. Legacy v1 and
v2 remain readable; an older APK may reject v3 and cannot apply archive flags. **Both
phone and tablet must install this update before checking cross-device totals.**
Keep the app installed, use the same Firebase UID and let queued writes finish.
Archive the import once on the affected device; the other updated device receives
the archive state. An offline/newer edit can still win according to the documented
version policy; there is no claim of a live-device result from mocked tests.

Repair validation covers a 220-original/220-import fixture plus a new transaction,
all 441 rows retained, active counts/totals, genuine identical reference entries,
insufficient originals, changed copies/counterparts, atomic stale-preview rejection,
restart/repeated repair, v3 restore, undo, stale snapshot protection, offline upload
payload and a second database's archive/undo download. UI cases cover preview/cancel,
confirmation, automatic pre-operation snapshot, visible corrected totals, undo,
changed preview and the separate picker. User backups are used only for a local Java
planner check; no financial data, notes or private files are included in this PR.


## Narrow screens and large text

The dashboard previously forced three single-line currency amounts into equal narrow
columns and used fixed heights for business selectors, period tabs, summary cards,
quick actions, chart heading and bottom navigation. Long labels and larger Android
font sizes could therefore be clipped even though the stored/calculated values were
unchanged.

Phone windows use stacked full-width summary cards, two-row period tabs and full-width
chart/search headings. Large font settings also stack business selectors and quick
buttons, and use two rows for navigation. Wider windows keep columns. Text/control
heights wrap content with minimum touch heights; currency text can wrap and is never
ellipsized or abbreviated. Transaction notes and dates have their own full-width area,
with the full amount and delete button below. Report columns follow the same compact
layout. Android's user font size remains respected; no font-scale override is used.

Native text-layout regression tests measure 320dp/1.8x, 360dp/1x, 800dp tablet and short
landscape windows, plus 320dp/2x with large currency values and long notes. They assert
all laid-out text ends, line widths/heights, exact formatted amounts, usable scroll
height and reachable navigation, rather than only inspecting LayoutParams. A real
screenshot on the affected device remains useful to verify its specific font/display
settings. This UI change does not alter transaction identity, archive or totals logic.
