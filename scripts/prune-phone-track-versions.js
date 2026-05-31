#!/usr/bin/env node
/**
 * Remove Wear CI versionCodes (>1) from phone Play tracks (internal, alpha, beta, production).
 * Fixes Console error: android.hardware.type.watch required — remove artifact from current track.
 *
 * Usage:
 *   PLAY_SERVICE_ACCOUNT_JSON='...' node scripts/prune-phone-track-versions.js --package com.eazpire.creator.wear
 *   ... --dry-run
 */
const PHONE_TRACKS = new Set(['internal', 'alpha', 'beta', 'production', 'rollout']);
const WEAR_MIN_REMOVED = 2; // keep only legacy versionCode 1 on phone tracks if any

function parseArgs() {
  const args = process.argv.slice(2);
  return {
    package: args.includes('--package') ? args[args.indexOf('--package') + 1] : '',
    dryRun: args.includes('--dry-run'),
  };
}

async function getPublisher() {
  const raw = process.env.PLAY_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error('PLAY_SERVICE_ACCOUNT_JSON missing');
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({
    credentials: JSON.parse(raw),
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  return google.androidpublisher({ version: 'v3', auth });
}

function pruneReleases(releases) {
  let changed = false;
  const next = [];
  for (const rel of releases || []) {
    const codes = (rel.versionCodes || []).map((c) => Number(c));
    const kept = codes.filter((c) => c < WEAR_MIN_REMOVED).map(String);
    if (kept.length !== codes.length) {
      changed = true;
      if (kept.length === 0) continue;
      next.push({ ...rel, versionCodes: kept });
    } else {
      next.push(rel);
    }
  }
  if (next.length !== (releases || []).length) changed = true;
  return { releases: next, changed };
}

async function main() {
  const { package: packageName, dryRun } = parseArgs();
  if (!packageName) {
    console.error('Usage: node scripts/prune-phone-track-versions.js --package com.eazpire.creator.wear [--dry-run]');
    process.exit(1);
  }

  const publisher = await getPublisher();
  const insert = await publisher.edits.insert({ packageName });
  const editId = insert.data.id;
  console.log(`Edit ${editId} for ${packageName}${dryRun ? ' (dry-run)' : ''}`);

  try {
    const list = await publisher.edits.tracks.list({ packageName, editId });
    const tracks = list.data.tracks || [];
    let anyChange = false;

    for (const t of tracks) {
      const name = t.track || '';
      if (name.startsWith('wear:') || !PHONE_TRACKS.has(name)) continue;

      const { releases, changed } = pruneReleases(t.releases);
      if (!changed) {
        console.log(`  ${name}: nothing to prune`);
        continue;
      }

      const removed = [];
      for (const rel of t.releases || []) {
        for (const vc of rel.versionCodes || []) {
          if (Number(vc) >= WEAR_MIN_REMOVED) removed.push(Number(vc));
        }
      }
      console.log(`  ${name}: remove versionCodes ${[...new Set(removed)].join(', ')}`);
      anyChange = true;

      if (!dryRun) {
        await publisher.edits.tracks.update({
          packageName,
          editId,
          track: name,
          requestBody: { track: name, releases },
        });
      }
    }

    if (!anyChange) {
      console.log('OK: no Wear versionCodes on phone tracks.');
      await publisher.edits.delete({ packageName, editId });
      return;
    }

    if (dryRun) {
      console.log('Dry-run: not committing.');
      await publisher.edits.delete({ packageName, editId });
      return;
    }

    console.log('Committing edit…');
    await publisher.edits.commit({ packageName, editId, changesNotSentForReview: true });
    console.log('OK: phone tracks pruned. Wear bundles should only remain on wear:* tracks.');
  } catch (e) {
    try {
      await publisher.edits.delete({ packageName, editId });
    } catch {
      /* ignore */
    }
    throw e;
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
