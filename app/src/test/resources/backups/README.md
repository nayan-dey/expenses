# Saved copies of old backups

Each file here is the `kharcha.json` from a backup, exactly as one version of the app wrote it.
`BackupCompatTest` opens every one of them with today's code on every build.

- **Never edit or delete a file here.** They stand for backups sitting on real phones.
- When the data model changes (`core/model/Models.kt`), add a new file written the new way and
  a test for it. The old files stay and must keep passing.
- Only made-up data. The repo is public.

| File | Written the way of |
|---|---|
| `v1-first-release.json` | the first version (commit `663cebb`): sheets, categories, items, transfers |
| `v2-friends-and-goals.json` | the second batch (commit `de58352`): friends, splits, settling up, paid-with, goals, streak days |
| `v3-covered-people.json` | split bills that include people you pay for (`Item.covered`) |
