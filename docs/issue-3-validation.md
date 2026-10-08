# Issue #3 validation

The app retains `kocaaslan_is_takip.db` and upgrades it in place to schema 3.
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
These tests validate visibility and calculations for fixture data, not the user's
actual backup or amounts, which have not been provided.
