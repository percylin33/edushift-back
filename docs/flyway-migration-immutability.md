# Flyway migration immutability

Once a Flyway migration is committed or applied to a database, do not edit, delete, rename, or reuse it. Create a new versioned migration for follow-up changes.

For each new migration, register its checksum before applying it, then verify the working tree:

```powershell
node scripts/check-flyway-immutability.mjs --register-new
node scripts/check-flyway-immutability.mjs --worktree
```

Commit the migration together with `scripts/flyway-immutable-baseline.json`. The backend pull-request workflow rejects changed or removed existing migrations and checks that newly added versions are registered.

The local Git pre-commit hook also runs the same check. To enable it in another clone, configure `core.hooksPath` as `.githooks` once from the backend repository root.

If Flyway reports a checksum mismatch, stop and investigate. Restore the exact original migration or add a corrective migration after comparing the live schema. Do not run `flyway repair` to hide the mismatch.
