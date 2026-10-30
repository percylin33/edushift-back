#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const migrationDirectory = path.join(repositoryRoot, 'src', 'main', 'resources', 'db', 'migration');
const baselinePath = path.join(path.dirname(fileURLToPath(import.meta.url)), 'flyway-immutable-baseline.json');
const migrationPattern = /^V(\d+(?:[._]\d+)*)__.*\.sql$/;

function migrationVersion(filename) {
  return filename.match(migrationPattern)?.[1].replaceAll('_', '.') ?? null;
}

function hash(contents) {
  const text = Buffer.isBuffer(contents) ? contents.toString('utf8') : String(contents);
  const normalizedText = text.replace(/\r\n?/g, '\n');
  return createHash('sha256').update(normalizedText, 'utf8').digest('hex');
}

function compareVersions(left, right) {
  const a = left.split('.').map(Number);
  const b = right.split('.').map(Number);
  for (let index = 0; index < Math.max(a.length, b.length); index++) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

function readBaseline() {
  return JSON.parse(readFileSync(baselinePath, 'utf8')).protectedVersions;
}

function listMigrations() {
  const byVersion = new Map();
  for (const filename of readdirSync(migrationDirectory)) {
    const version = migrationVersion(filename);
    if (!version) continue;
    const filenames = byVersion.get(version) ?? [];
    filenames.push(filename);
    byVersion.set(version, filenames);
  }
  return byVersion;
}

function validateMigrationTree(protectedVersions, { requireAllProtected = true, requireAllFilesProtected = true } = {}) {
  const violations = [];
  const byVersion = listMigrations();
  const protectedKeys = Object.keys(protectedVersions);
  const highestProtectedVersion = protectedKeys.sort(compareVersions).at(-1);

  for (const [version, expectedHash] of Object.entries(protectedVersions)) {
    const filenames = byVersion.get(version) ?? [];
    if (filenames.length !== 1) {
      if (requireAllProtected) {
        violations.push(filenames.length === 0 ? `V${version} is missing or renamed` : `V${version} has duplicate files`);
      }
      continue;
    }

    const actualHash = hash(readFileSync(path.join(migrationDirectory, filenames[0])));
    if (actualHash !== expectedHash) violations.push(`${filenames[0]} checksum differs from the protected baseline`);
  }

  for (const [version, filenames] of byVersion) {
    if (filenames.length > 1) violations.push(`V${version} has duplicate files`);
    if (version in protectedVersions) continue;

    if (highestProtectedVersion && compareVersions(version, highestProtectedVersion) <= 0) {
      violations.push(`${filenames[0]} reuses a version at or below the protected baseline`);
    } else if (requireAllFilesProtected) {
      violations.push(`${filenames[0]} has no recorded checksum; run --register-new before applying it`);
    }
  }

  return violations;
}

function checkWorkingTree() {
  const protectedVersions = readBaseline();
  const violations = validateMigrationTree(protectedVersions);
  const committedManifest = gitShowJson('HEAD', 'scripts/flyway-immutable-baseline.json', { optional: true });
  for (const [version, checksum] of Object.entries(committedManifest?.protectedVersions ?? {})) {
    if (protectedVersions[version] !== checksum) {
      violations.push(`committed checksum for V${version} was changed or removed`);
    }
  }

  if (violations.length > 0) {
    console.error('Flyway immutability check failed.');
    for (const violation of [...new Set(violations)]) console.error(`- ${violation}`);
    console.error('Restore the protected migration; use a new version and register its checksum for schema changes.');
    process.exit(1);
  }
  console.log('Flyway immutability check passed: all migration files match the protected baseline.');
}

function registerNewMigrations() {
  const protectedVersions = readBaseline();
  const existingViolations = validateMigrationTree(protectedVersions, {
    requireAllProtected: true,
    requireAllFilesProtected: false,
  });
  if (existingViolations.length > 0) {
    console.error('Cannot register migrations because an existing protected migration is missing, renamed, duplicated, or changed.');
    for (const violation of [...new Set(existingViolations)]) console.error(`- ${violation}`);
    process.exit(1);
  }

  const byVersion = listMigrations();
  const highestProtectedVersion = Object.keys(protectedVersions).sort(compareVersions).at(-1);
  const newEntries = [];
  for (const [version, filenames] of byVersion) {
    if (version in protectedVersions) continue;
    if (filenames.length !== 1) {
      console.error(`Cannot register duplicate migration version V${version}.`);
      process.exit(1);
    }
    if (highestProtectedVersion && compareVersions(version, highestProtectedVersion) <= 0) {
      console.error(`Cannot register ${filenames[0]}: its version is not newer than the protected baseline.`);
      process.exit(1);
    }
    newEntries.push([version, hash(readFileSync(path.join(migrationDirectory, filenames[0])))]);
  }

  if (newEntries.length === 0) {
    console.log('No new migration versions to register.');
    return;
  }

  for (const [version, checksum] of newEntries) protectedVersions[version] = checksum;
  const sortedVersions = Object.keys(protectedVersions).sort(compareVersions);
  const orderedVersions = Object.fromEntries(sortedVersions.map((version) => [version, protectedVersions[version]]));
  writeFileSync(baselinePath, `${JSON.stringify({ protectedVersions: orderedVersions }, null, 2)}\n`);
  console.log(`Registered ${newEntries.length} new migration checksum(s). Run --worktree and commit the manifest with the migration.`);
}

function gitShowJson(ref, filename, { optional = false } = {}) {
  let contents;
  try {
    contents = execFileSync('git', ['show', `${ref}:${filename}`], {
      cwd: repositoryRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  } catch (error) {
    if (optional) return null;
    throw error;
  }
  return JSON.parse(contents);
}

function checkPullRequest(baseSha, headSha) {
  const baseManifest = gitShowJson(baseSha, 'scripts/flyway-immutable-baseline.json', { optional: true });
  const headManifest = gitShowJson(headSha, 'scripts/flyway-immutable-baseline.json');
  const baseProtected = baseManifest?.protectedVersions ?? {};
  const headProtected = headManifest.protectedVersions ?? {};
  const violations = [];

  for (const [version, checksum] of Object.entries(baseProtected)) {
    if (headProtected[version] !== checksum) {
      violations.push(`protected checksum for V${version} was changed or removed`);
    }
  }

  const highestBaseVersion = Object.keys(baseProtected).sort(compareVersions).at(-1);
  for (const version of Object.keys(headProtected)) {
    if (!(version in baseProtected) && highestBaseVersion && compareVersions(version, highestBaseVersion) <= 0) {
      violations.push(`new protected version V${version} is not newer than the base baseline`);
    }
  }

  violations.push(...validateMigrationTree(headProtected));

  const changedFiles = execFileSync(
    'git',
    ['diff', '--name-status', '--find-renames', `${baseSha}...${headSha}`, '--', 'src/main/resources/db/migration'],
    { cwd: repositoryRoot, encoding: 'utf8' },
  ).trim();

  for (const line of changedFiles ? changedFiles.split(/\r?\n/) : []) {
    const [status, ...paths] = line.split('\t');
    const migrationPaths = paths.filter((file) => migrationVersion(path.posix.basename(file)) !== null);

    if (/^(M|D|R|T|U)/.test(status) && migrationPaths.length > 0) {
      violations.push(...migrationPaths.map((file) => `${file} was modified, deleted, or renamed`));
    }
  }

  if (violations.length > 0) {
    console.error('Flyway migration immutability check failed.');
    for (const violation of [...new Set(violations)]) {
      console.error(`::error file=${violation}::Do not modify, delete, rename, or reuse a committed migration. Add a new version.`);
    }
    process.exit(1);
  }

  console.log('Flyway immutability check passed: protected migrations are unchanged and additions are registered.');
}

const [modeOrBaseSha, headSha] = process.argv.slice(2);
if (modeOrBaseSha === '--worktree') {
  checkWorkingTree();
} else if (modeOrBaseSha === '--register-new') {
  registerNewMigrations();
} else if (modeOrBaseSha && headSha) {
  checkPullRequest(modeOrBaseSha, headSha);
} else {
  console.error('Usage: node scripts/check-flyway-immutability.mjs --worktree');
  console.error('   or: node scripts/check-flyway-immutability.mjs --register-new');
  console.error('   or: node scripts/check-flyway-immutability.mjs <base-sha> <head-sha>');
  process.exit(2);
}
